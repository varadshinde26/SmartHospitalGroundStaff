package com.smarthospital.controller;

import com.smarthospital.model.User;
import com.smarthospital.service.PhpBridge;
import com.smarthospital.util.Access;
import com.smarthospital.util.Audit;
import com.smarthospital.util.DB;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * Explainable, rule-based smart duty allocation.
 * Hard eligibility checks are performed first; only eligible staff are scored.
 */
@WebServlet("/allocation")
public class AllocationServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        String dutyParam = req.getParameter("duty_id");
        if (dutyParam == null || dutyParam.isBlank()) {
            resp.getWriter().print("[]");
            return;
        }
        try (Connection c = DB.getConnection()) {
            User u = (User) req.getSession().getAttribute("user");
            int dutyId = Integer.parseInt(dutyParam);
            if (!Access.canAccessDuty(c, u, dutyId)) {
                resp.sendError(403, "Duty is outside your department scope.");
                return;
            }

            DutyInfo duty = getDuty(c, dutyId);
            if (duty == null) {
                resp.getWriter().print("[]");
                return;
            }

            List<Integer> requiredSkills = requiredSkills(c, dutyId);
            List<Map<String, Object>> candidates = new ArrayList<>();

            String staffSql = "SELECT s.staff_id,s.name,s.staff_code,s.experience_years " +
                    "FROM staff s WHERE s.status='ACTIVE' AND s.department_id=? AND s.role_id=? ORDER BY s.name";
            try (PreparedStatement p = c.prepareStatement(staffSql)) {
                p.setInt(1, duty.departmentId);
                p.setInt(2, duty.roleId);
                try (ResultSet r = p.executeQuery()) {
                    while (r.next()) {
                        int staffId = r.getInt("staff_id");
                        Eligibility e = checkEligibility(c, staffId, duty, requiredSkills);
                        if (!e.eligible) continue;

                        double hours = dutyHours(c, staffId, duty.date);
                        int recentCount = recentDutyCount(c, staffId, duty.date);
                        double skill = requiredSkills.isEmpty() ? 100.0 : 100.0 * e.matchedSkills / requiredSkills.size();
                        double availability = 100.0;
                        double workload = Math.max(0.0, 100.0 - (hours / 30.0 * 100.0));
                        double experience = Math.min(100.0, r.getDouble("experience_years") / 5.0 * 100.0);
                        double fairness = Math.max(0.0, 100.0 - recentCount * 8.0);
                        double score = .30 * skill + .25 * availability + .20 * workload + .15 * experience + .10 * fairness;

                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("staffId", staffId);
                        m.put("name", r.getString("name"));
                        m.put("code", r.getString("staff_code"));
                        m.put("skill", round(skill));
                        m.put("availability", round(availability));
                        m.put("workload", round(workload));
                        m.put("experience", round(experience));
                        m.put("fairness", round(fairness));
                        m.put("score", round(score));
                        m.put("hours", round(hours));
                        m.put("reasons", Arrays.asList(
                                "Role and department matched",
                                e.availabilityReason,
                                e.leaveReason,
                                "No overlapping duty",
                                requiredSkills.isEmpty() ? "No specific skills required" : "All required skills matched"
                        ));
                        candidates.add(m);
                    }
                }
            }
            candidates.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));
            resp.getWriter().print(toJson(candidates));
        } catch (Exception e) {
            // Never silently turn a database/logic error into an unexplained empty recommendation list.
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().print("{\"error\":\"" + jsonEscape(e.getMessage() == null ? "Allocation service error" : e.getMessage()) + "\"}");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = (User) req.getSession().getAttribute("user");
        try (Connection c = DB.getConnection()) {
            int dutyId = Integer.parseInt(req.getParameter("duty_id"));
            int staffId = Integer.parseInt(req.getParameter("staff_id"));
            if (!Access.canAccessDuty(c, u, dutyId) || !Access.canAccessStaff(c, u, staffId)) {
                throw new SecurityException("Duty or staff member is outside your department scope.");
            }
            DutyInfo duty = getDuty(c, dutyId);
            if (duty == null) throw new IllegalArgumentException("Duty not found.");
            if ("CANCELLED".equals(duty.status) || "COMPLETED".equals(duty.status)) {
                throw new IllegalArgumentException("This duty can no longer be assigned.");
            }
            if (existsAssignment(c, dutyId)) {
                throw new IllegalArgumentException("This duty already has an active assignment.");
            }
            List<Integer> requiredSkills = requiredSkills(c, dutyId);
            Eligibility e = checkEligibility(c, staffId, duty, requiredSkills);
            if (!e.eligible) throw new IllegalArgumentException(e.failureReason);

            c.setAutoCommit(false);
            try {
                try (PreparedStatement p = c.prepareStatement("INSERT INTO duty_assignments(duty_id,staff_id,assigned_by) VALUES(?,?,?)")) {
                    p.setInt(1, dutyId); p.setInt(2, staffId); p.setInt(3, u.id()); p.executeUpdate();
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE duties SET status='ASSIGNED' WHERE duty_id=?")) {
                    p.setInt(1, dutyId); p.executeUpdate();
                }
                refreshWorkload(c, staffId, duty.date);
                c.commit();
            } catch (Exception ex) {
                c.rollback();
                throw ex;
            } finally {
                c.setAutoCommit(true);
            }

            int userId = 0;
            String email = null, name = null;
            try (PreparedStatement p = c.prepareStatement("SELECT user_id,email,name FROM staff WHERE staff_id=?")) {
                p.setInt(1, staffId);
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) { userId = r.getInt(1); email = r.getString(2); name = r.getString(3); }
                }
            }
            if (userId > 0) {
                try (PreparedStatement p = c.prepareStatement("INSERT INTO notifications(user_id,message,type) VALUES(?,?,?)")) {
                    p.setInt(1, userId);
                    p.setString(2, "A new duty has been assigned to you. Open My Duties to acknowledge it.");
                    p.setString(3, "DUTY_ASSIGNED");
                    p.executeUpdate();
                }
            }
            if (email != null && !email.isBlank()) PhpBridge.sendDutyMail(email, name, duty.name);
            Audit.log(u.id(), "DUTY_ASSIGNED", "Duty #" + dutyId + " assigned to staff #" + staffId);
            resp.sendRedirect(req.getContextPath() + "/duties");
        } catch (SecurityException e) {
            resp.sendError(403, e.getMessage());
        } catch (Exception e) {
            resp.sendError(400, e.getMessage() == null ? "Unable to assign duty." : e.getMessage());
        }
    }

    private DutyInfo getDuty(Connection c, int dutyId) throws SQLException {
        String q = "SELECT duty_id,duty_name,department_id,role_id,duty_date,start_time,end_time,status FROM duties WHERE duty_id=?";
        try (PreparedStatement p = c.prepareStatement(q)) {
            p.setInt(1, dutyId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                return new DutyInfo(r.getInt(1), r.getString(2), r.getInt(3), r.getInt(4), r.getDate(5), r.getTime(6), r.getTime(7), r.getString(8));
            }
        }
    }

    private List<Integer> requiredSkills(Connection c, int dutyId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT skill_id FROM duty_required_skills WHERE duty_id=?")) {
            p.setInt(1, dutyId);
            try (ResultSet r = p.executeQuery()) { while (r.next()) ids.add(r.getInt(1)); }
        }
        return ids;
    }

    private Eligibility checkEligibility(Connection c, int staffId, DutyInfo duty, List<Integer> requiredSkills) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT department_id,role_id FROM staff WHERE staff_id=? AND status='ACTIVE'")) {
            p.setInt(1, staffId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return Eligibility.no("Staff member is not active.");
                if (r.getInt(1) != duty.departmentId) return Eligibility.no("Staff member belongs to another work area.");
                if (r.getInt(2) != duty.roleId) return Eligibility.no("Staff role does not match the duty role.");
            }
        }

        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM leaves WHERE staff_id=? AND status='APPROVED' AND ? BETWEEN start_date AND end_date LIMIT 1")) {
            p.setInt(1, staffId); p.setDate(2, duty.date);
            try (ResultSet r = p.executeQuery()) { if (r.next()) return Eligibility.no("Staff member is on approved leave on the duty date."); }
        }

        boolean hasAvailabilityRows;
        try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM staff_availability WHERE staff_id=? AND available_date=?")) {
            p.setInt(1, staffId); p.setDate(2, duty.date);
            try (ResultSet r = p.executeQuery()) { r.next(); hasAvailabilityRows = r.getInt(1) > 0; }
        }
        if (hasAvailabilityRows) {
            try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM staff_availability WHERE staff_id=? AND available_date=? AND status='AVAILABLE' AND start_time<=? AND end_time>=? LIMIT 1")) {
                p.setInt(1, staffId); p.setDate(2, duty.date); p.setTime(3, duty.start); p.setTime(4, duty.end);
                try (ResultSet r = p.executeQuery()) { if (!r.next()) return Eligibility.no("Staff member is not available for the full duty window."); }
            }
        }

        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM duty_assignments a JOIN duties d ON a.duty_id=d.duty_id WHERE a.staff_id=? AND d.duty_date=? AND d.status<>'CANCELLED' AND d.start_time < ? AND d.end_time > ? LIMIT 1")) {
            p.setInt(1, staffId); p.setDate(2, duty.date); p.setTime(3, duty.end); p.setTime(4, duty.start);
            try (ResultSet r = p.executeQuery()) { if (r.next()) return Eligibility.no("Staff member has an overlapping duty."); }
        }

        int matched = 0;
        for (Integer skillId : requiredSkills) {
            try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM staff_skills WHERE staff_id=? AND skill_id=? AND verification_status='VERIFIED'")) {
                p.setInt(1, staffId); p.setInt(2, skillId);
                try (ResultSet r = p.executeQuery()) { if (r.next()) matched++; }
            }
        }
        if (matched != requiredSkills.size()) return Eligibility.no("One or more required skills are missing or not verified.");

        Eligibility ok = new Eligibility(true, matched, "Available for the full duty window", "No approved leave on duty date", "");
        return ok;
    }

    private boolean existsAssignment(Connection c, int dutyId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM duty_assignments WHERE duty_id=? AND status<>'CANCELLED' LIMIT 1")) {
            p.setInt(1, dutyId); try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }

    private double dutyHours(Connection c, int staffId, java.sql.Date date) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE,start_time,end_time))/60,0) FROM duties d JOIN duty_assignments a ON a.duty_id=d.duty_id WHERE a.staff_id=? AND d.duty_date=? AND a.status<>'CANCELLED'")) {
            p.setInt(1, staffId); p.setDate(2, date); try (ResultSet r = p.executeQuery()) { r.next(); return r.getDouble(1); }
        }
    }

    private int recentDutyCount(Connection c, int staffId, java.sql.Date date) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM duties d JOIN duty_assignments a ON a.duty_id=d.duty_id WHERE a.staff_id=? AND d.duty_date BETWEEN DATE_SUB(?,INTERVAL 30 DAY) AND ? AND a.status<>'CANCELLED'")) {
            p.setInt(1, staffId); p.setDate(2, date); p.setDate(3, date); try (ResultSet r = p.executeQuery()) { r.next(); return r.getInt(1); }
        }
    }

    private static void refreshWorkload(Connection c, int staffId, java.sql.Date date) throws SQLException {
        String q = "INSERT INTO workload(staff_id,workload_date,duty_hours,duty_count,workload_level) " +
                "SELECT ?,?,COALESCE(SUM(TIMESTAMPDIFF(MINUTE,d.start_time,d.end_time))/60,0),COUNT(*)," +
                "CASE WHEN COALESCE(SUM(TIMESTAMPDIFF(MINUTE,d.start_time,d.end_time))/60,0)>=40 THEN 'OVERLOAD' " +
                "WHEN COALESCE(SUM(TIMESTAMPDIFF(MINUTE,d.start_time,d.end_time))/60,0)>=30 THEN 'HIGH' " +
                "WHEN COALESCE(SUM(TIMESTAMPDIFF(MINUTE,d.start_time,d.end_time))/60,0)>=16 THEN 'MODERATE' ELSE 'LOW' END " +
                "FROM duties d JOIN duty_assignments a ON a.duty_id=d.duty_id WHERE a.staff_id=? AND d.duty_date=? AND a.status<>'CANCELLED' " +
                "ON DUPLICATE KEY UPDATE duty_hours=VALUES(duty_hours),duty_count=VALUES(duty_count),workload_level=VALUES(workload_level)";
        try (PreparedStatement p = c.prepareStatement(q)) { p.setInt(1, staffId); p.setDate(2, date); p.setInt(3, staffId); p.setDate(4, date); p.executeUpdate(); }
    }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
    private static String jsonEscape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " "); }
    private static String toJson(List<Map<String,Object>> rows) {
        StringBuilder b = new StringBuilder("[");
        for (int i=0;i<rows.size();i++) {
            if(i>0)b.append(','); Map<String,Object> m=rows.get(i); b.append('{'); int j=0;
            for(Map.Entry<String,Object> e:m.entrySet()){if(j++>0)b.append(',');b.append('"').append(jsonEscape(e.getKey())).append('"').append(':');Object v=e.getValue();
                if(v instanceof Number)b.append(v); else if(v instanceof List){b.append('[');List<?> l=(List<?>)v;for(int k=0;k<l.size();k++){if(k>0)b.append(',');b.append('"').append(jsonEscape(String.valueOf(l.get(k)))).append('"');}b.append(']');}
                else b.append('"').append(jsonEscape(String.valueOf(v))).append('"');}
            b.append('}');
        }
        return b.append(']').toString();
    }

    private static final class DutyInfo {
        final int id, departmentId, roleId; final String name,status; final java.sql.Date date; final Time start,end;
        DutyInfo(int id,String name,int departmentId,int roleId,java.sql.Date date,Time start,Time end,String status){this.id=id;this.name=name;this.departmentId=departmentId;this.roleId=roleId;this.date=date;this.start=start;this.end=end;this.status=status;}
    }
    private static final class Eligibility {
        final boolean eligible; final int matchedSkills; final String availabilityReason,leaveReason,failureReason;
        Eligibility(boolean eligible,int matchedSkills,String availabilityReason,String leaveReason,String failureReason){this.eligible=eligible;this.matchedSkills=matchedSkills;this.availabilityReason=availabilityReason;this.leaveReason=leaveReason;this.failureReason=failureReason;}
        static Eligibility no(String reason){return new Eligibility(false,0,"","",reason);}
    }
}
