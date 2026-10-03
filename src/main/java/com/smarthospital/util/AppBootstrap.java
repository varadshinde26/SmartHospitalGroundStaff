package com.smarthospital.util;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import java.sql.*;

/** Creates the demo accounts and a small, repeatable demonstration workforce. */
public class AppBootstrap implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent event) {
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            seedUser(c, "admin", "admin@123", "ADMIN", "System Administrator", "admin@hospital.local");
            seedUser(c, "supervisor", "supervisor@123", "SUPERVISOR", "ICU Supervisor", "supervisor@hospital.local");
            seedUser(c, "staff101", "staff@123", "STAFF", "Rahul Patil", "rahul@example.com");
            seedUser(c, "ramesh101", "ramesh@123", "STAFF", "Ramesh Patil", "ramesh@example.com");
            int supervisorId=userId(c,"supervisor");
            int icuId=id(c,"departments","department_name","ICU");
            try(PreparedStatement p=c.prepareStatement("INSERT IGNORE INTO supervisor_departments(supervisor_user_id,department_id,active) VALUES(?,?,1)")){p.setInt(1,supervisorId);p.setInt(2,icuId);p.executeUpdate();}

            int rahul = ensureStaff(c, "staff101", "STF-101", "Rahul Patil", "9876543210", "rahul@example.com", "ICU", "ICU Attendant", 3.0);
            int neha = ensureStaff(c, null, "STF-102", "Neha Kulkarni", "9876543211", "neha@example.com", "General Ward", "Ward Attendant", 5.0);
            int amit = ensureStaff(c, null, "STF-103", "Amit Joshi", "9876543212", "amit@example.com", "Emergency/Casualty", "Emergency Attendant", 2.0);
            int ramesh = ensureStaff(c, "ramesh101", "STF-104", "Ramesh Patil", "9876543213", "ramesh@example.com", "ICU", "ICU Attendant", 4.0);

            addSkillIfMissing(c, rahul, "Patient Handling");
            addSkillIfMissing(c, rahul, "Basic First Aid");
            addSkillIfMissing(c, rahul, "ICU Equipment Assistance");
            addSkillIfMissing(c, rahul, "Infection Control");

            addSkillIfMissing(c, neha, "Patient Handling");
            addSkillIfMissing(c, neha, "Basic First Aid");
            addSkillIfMissing(c, neha, "Infection Control");

            addSkillIfMissing(c, amit, "Patient Handling");
            addSkillIfMissing(c, amit, "Basic First Aid");
            addSkillIfMissing(c, amit, "Emergency Response");

            addSkillIfMissing(c, ramesh, "Patient Handling");
            addSkillIfMissing(c, ramesh, "Basic First Aid");
            addSkillIfMissing(c, ramesh, "ICU Equipment Assistance");
            addSkillIfMissing(c, ramesh, "Infection Control");

            seedAvailability(c, rahul);
            seedAvailability(c, neha);
            seedAvailability(c, amit);
            seedAvailability(c, ramesh);
            seedDemoDuty(c, supervisorId(c));

            c.commit();
        } catch (Exception ex) {
            event.getServletContext().log("Smart Hospital bootstrap failed. Check MySQL and Connector/J.", ex);
        }
    }

    private static void seedUser(Connection c, String username, String password, String role, String fullName, String email) throws Exception {
        Integer id = null;
        try (PreparedStatement p = c.prepareStatement("SELECT user_id FROM users WHERE username=?")) {
            p.setString(1, username);
            try (ResultSet r = p.executeQuery()) { if (r.next()) id = r.getInt(1); }
        }
        String hash = PasswordUtil.hash(password);
        if (id == null) {
            try (PreparedStatement p = c.prepareStatement("INSERT INTO users(username,password_hash,role,status,full_name,email) VALUES(?,?,?,'ACTIVE',?,?)")) {
                p.setString(1, username); p.setString(2, hash); p.setString(3, role); p.setString(4, fullName); p.setString(5, email); p.executeUpdate();
            }
        } else {
            try (PreparedStatement p = c.prepareStatement("UPDATE users SET password_hash=?,role=?,status='ACTIVE',full_name=?,email=? WHERE user_id=?")) {
                p.setString(1, hash); p.setString(2, role); p.setString(3, fullName); p.setString(4, email); p.setInt(5, id); p.executeUpdate();
            }
        }
    }

    private static int ensureStaff(Connection c, String username, String code, String name, String phone,
                                   String email, String department, String role, double experience) throws Exception {
        Integer staffId = null;
        try (PreparedStatement p = c.prepareStatement("SELECT staff_id FROM staff WHERE staff_code=?")) {
            p.setString(1, code);
            try (ResultSet r = p.executeQuery()) { if (r.next()) staffId = r.getInt(1); }
        }
        Integer userId = null;
        if (username != null) userId = userId(c, username);
        int departmentId = id(c, "departments", "department_name", department);
        int roleId = id(c, "roles", "role_name", role);

        if (staffId == null) {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO staff(user_id,staff_code,name,phone,email,department_id,role_id,experience_years,status) VALUES(?,?,?,?,?,?,?,?,'ACTIVE')",
                    Statement.RETURN_GENERATED_KEYS)) {
                if (userId == null) p.setNull(1, Types.INTEGER); else p.setInt(1, userId);
                p.setString(2, code); p.setString(3, name); p.setString(4, phone); p.setString(5, email);
                p.setInt(6, departmentId); p.setInt(7, roleId); p.setDouble(8, experience); p.executeUpdate();
                try (ResultSet r = p.getGeneratedKeys()) { r.next(); staffId = r.getInt(1); }
            }
        } else {
            try (PreparedStatement p = c.prepareStatement("UPDATE staff SET user_id=?,name=?,phone=?,email=?,department_id=?,role_id=?,experience_years=?,status='ACTIVE' WHERE staff_id=?")) {
                if (userId == null) p.setNull(1, Types.INTEGER); else p.setInt(1, userId);
                p.setString(2, name); p.setString(3, phone); p.setString(4, email); p.setInt(5, departmentId);
                p.setInt(6, roleId); p.setDouble(7, experience); p.setInt(8, staffId); p.executeUpdate();
            }
        }
        return staffId;
    }

    private static void addSkillIfMissing(Connection c, int staffId, String skill) throws Exception {
        int skillId = id(c, "skills", "skill_name", skill);
        try (PreparedStatement p = c.prepareStatement("INSERT IGNORE INTO staff_skills(staff_id,skill_id,verification_status) VALUES(?,?,'VERIFIED')")) {
            p.setInt(1, staffId); p.setInt(2, skillId); p.executeUpdate();
        }
    }

    private static void seedAvailability(Connection c, int staffId) throws Exception {
        // Keep the next 31 days populated even when the application is started after an older seed.
        for (int day = 0; day < 31; day++) {
            java.sql.Date date = java.sql.Date.valueOf(java.time.LocalDate.now().plusDays(day));
            try (PreparedStatement check = c.prepareStatement("SELECT availability_id FROM staff_availability WHERE staff_id=? AND available_date=? LIMIT 1")) {
                check.setInt(1, staffId); check.setDate(2, date);
                try (ResultSet r = check.executeQuery()) {
                    if (r.next()) continue;
                }
            }
            try (PreparedStatement p = c.prepareStatement("INSERT INTO staff_availability(staff_id,available_date,start_time,end_time,status) VALUES(?,?,?,?,?)")) {
                p.setInt(1, staffId); p.setDate(2, date); p.setTime(3, Time.valueOf("00:00:00"));
                p.setTime(4, Time.valueOf("23:59:59")); p.setString(5, "AVAILABLE"); p.executeUpdate();
            }
        }
    }

    private static void seedDemoDuty(Connection c, int supervisorId) throws Exception {
        int dept = id(c, "departments", "department_name", "ICU");
        int role = id(c, "roles", "role_name", "ICU Attendant");
        int skill = id(c, "skills", "skill_name", "Patient Handling");
        int dutyId;
        try (PreparedStatement p = c.prepareStatement("SELECT duty_id FROM duties WHERE duty_name='ICU Ground Support Demonstration' ORDER BY duty_id LIMIT 1")) {
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    dutyId = r.getInt(1);
                    // Repair the existing demonstration duty instead of leaving an older/broken seed untouched.
                    try (PreparedStatement u = c.prepareStatement("UPDATE duties SET department_id=?,role_id=?,duty_date=?,start_time=?,end_time=?,priority=? WHERE duty_id=? AND status='OPEN'")) {
                        u.setInt(1, dept); u.setInt(2, role); u.setDate(3, Date.valueOf(java.time.LocalDate.now().plusDays(1)));
                        u.setTime(4, Time.valueOf("09:00:00")); u.setTime(5, Time.valueOf("13:00:00")); u.setString(6, "HIGH"); u.setInt(7, dutyId); u.executeUpdate();
                    }
                    try (PreparedStatement chk = c.prepareStatement("SELECT 1 FROM duty_required_skills WHERE duty_id=? AND skill_id=?")) {
                        chk.setInt(1,dutyId); chk.setInt(2,skill);
                        try(ResultSet rr=chk.executeQuery()) {
                            if(!rr.next()) try(PreparedStatement ins=c.prepareStatement("INSERT INTO duty_required_skills(duty_id,skill_id) VALUES(?,?)")){ins.setInt(1,dutyId);ins.setInt(2,skill);ins.executeUpdate();}
                        }
                    }
                    return;
                }
            }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO duties(duty_name,department_id,role_id,duty_date,start_time,end_time,priority,status,description,created_by) VALUES(?,?,?,?,?,?,?,'OPEN',?,?)", Statement.RETURN_GENERATED_KEYS)) {
            p.setString(1, "ICU Ground Support Demonstration"); p.setInt(2, dept); p.setInt(3, role);
            p.setDate(4, Date.valueOf(java.time.LocalDate.now().plusDays(1))); p.setTime(5, Time.valueOf("09:00:00"));
            p.setTime(6, Time.valueOf("13:00:00")); p.setString(7, "HIGH");
            p.setString(8, "Demo duty for testing eligibility, workload-aware recommendations and assignment workflow."); p.setInt(9, supervisorId);
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) { r.next(); dutyId = r.getInt(1); }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT INTO duty_required_skills(duty_id,skill_id) VALUES(?,?)")) { p.setInt(1,dutyId); p.setInt(2,skill); p.executeUpdate(); }
    }

    private static int supervisorId(Connection c) throws Exception { return userId(c, "supervisor"); }

    private static int userId(Connection c, String username) throws Exception {
        try (PreparedStatement p = c.prepareStatement("SELECT user_id FROM users WHERE username=?")) {
            p.setString(1, username);
            try (ResultSet r = p.executeQuery()) { if (r.next()) return r.getInt(1); }
        }
        throw new IllegalStateException("User not found: " + username);
    }

    private static int id(Connection c, String table, String column, String value) throws Exception {
        String pk = table.equals("departments") ? "department_id" : table.equals("roles") ? "role_id" : "skill_id";
        try (PreparedStatement p = c.prepareStatement("SELECT " + pk + " FROM " + table + " WHERE " + column + "=?")) {
            p.setString(1, value);
            try (ResultSet r = p.executeQuery()) { if (r.next()) return r.getInt(1); }
        }
        throw new IllegalStateException("Missing seed value: " + table + "." + column + "=" + value);
    }
}
