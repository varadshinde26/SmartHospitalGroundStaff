package com.smarthospital.controller;

import com.smarthospital.model.User;
import com.smarthospital.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.annotation.*;
import java.io.*;
import java.sql.*;
import java.util.*;

/** Administrator-managed ground staff directory. Supervisors are read-only and department-scoped. */
@WebServlet("/staff")
public class StaffServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        User u = (User) req.getSession().getAttribute("user");
        if (Access.isStaff(u)) { resp.sendError(403); return; }
        try (Connection c = DB.getConnection()) {
            List<Map<String,Object>> rows = new ArrayList<>();
            String base = "SELECT s.staff_id,s.staff_code,s.name,s.phone,s.email,s.experience_years,s.status,d.department_name,r.role_name,s.user_id " +
                    "FROM staff s JOIN departments d ON s.department_id=d.department_id JOIN roles r ON s.role_id=r.role_id WHERE ";
            List<Object> params = new ArrayList<>();
            String pred = Access.departmentPredicate(u, "s.department_id", params, c);
            String q = base + pred + " ORDER BY s.name";
            try (PreparedStatement p = c.prepareStatement(q)) {
                for (int i=0;i<params.size();i++) p.setObject(i+1, params.get(i));
                try (ResultSet x = p.executeQuery()) {
                    while (x.next()) {
                        Map<String,Object> m = new LinkedHashMap<>();
                        for (int i=1;i<=10;i++) m.put("c"+i, x.getObject(i));
                        rows.add(m);
                    }
                }
            }
            req.setAttribute("rows", rows);
            req.setAttribute("departments", list(c,"departments","department_id","department_name",u));
            req.setAttribute("roles", list(c,"roles","role_id","role_name",null));
            req.setAttribute("skills", list(c,"skills","skill_id","skill_name",null));

            String editParam = req.getParameter("edit");
            if (Access.isAdmin(u) && editParam != null && !editParam.isBlank()) {
                int staffId = Integer.parseInt(editParam);
                Map<String,Object> edit = loadStaff(c, staffId);
                if (edit != null) {
                    req.setAttribute("editStaff", edit);
                    req.setAttribute("editSkills", loadSkillIds(c, staffId));
                }
            }
            req.getRequestDispatcher("/staff.jsp").forward(req, resp);
        } catch (Exception e) { throw new ServletException(e); }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        User u = (User) req.getSession().getAttribute("user");
        if (!Access.isAdmin(u)) { resp.sendError(403,"Only administrators can add or modify staff."); return; }
        String action = clean(req.getParameter("action"));
        try (Connection c = DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                if ("save".equals(action)) save(c, req, u);
                else if ("deactivate".equals(action)) setStatus(c, req, u, "INACTIVE");
                else if ("reactivate".equals(action)) setStatus(c, req, u, "ACTIVE");
                else throw new IllegalArgumentException("Unsupported staff action.");
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally { c.setAutoCommit(true); }
            resp.sendRedirect(req.getContextPath()+"/staff");
        } catch (Exception e) { resp.sendError(400, e.getMessage()==null?"Unable to save staff.":e.getMessage()); }
    }

    private void save(Connection c, HttpServletRequest req, User admin) throws Exception {
        String idRaw = clean(req.getParameter("staff_id"));
        boolean editing = !idRaw.isBlank();
        Integer staffId = editing ? Integer.valueOf(idRaw) : null;
        int dept = Integer.parseInt(req.getParameter("department_id"));
        int role = Integer.parseInt(req.getParameter("role_id"));
        String code = clean(req.getParameter("staff_code"));
        String name = clean(req.getParameter("name"));
        String phone = clean(req.getParameter("phone"));
        String email = clean(req.getParameter("email"));
        double exp = Double.parseDouble(req.getParameter("experience_years"));
        if(code.isBlank() || name.isBlank()) throw new IllegalArgumentException("Staff code and name are required.");
        if(exp < 0 || exp > 60) throw new IllegalArgumentException("Experience must be between 0 and 60 years.");

        Integer existingUserId = null;
        String existingUsername = "";
        if (editing) {
            try (PreparedStatement p=c.prepareStatement("SELECT user_id FROM staff WHERE staff_id=?")) {
                p.setInt(1,staffId); try(ResultSet r=p.executeQuery()){ if(!r.next()) throw new IllegalArgumentException("Staff member not found."); existingUserId=(Integer)r.getObject(1); }
            }
            if(existingUserId!=null) {
                try(PreparedStatement p=c.prepareStatement("SELECT username FROM users WHERE user_id=?")){p.setInt(1,existingUserId);try(ResultSet r=p.executeQuery()){if(r.next())existingUsername=r.getString(1);}}
            }
        }

        String requestedUsername = clean(req.getParameter("login_username"));
        String password = clean(req.getParameter("login_password"));
        if(editing && requestedUsername.isBlank() && existingUserId!=null) requestedUsername = existingUsername;
        if((!requestedUsername.isBlank() && !editing && password.isBlank()) ||
           (!requestedUsername.isBlank() && editing && existingUserId==null && password.isBlank())) {
            throw new IllegalArgumentException("Enter a password when creating a new staff login.");
        }
        if(!password.isBlank() && password.length()<8) throw new IllegalArgumentException("Staff login password must be at least 8 characters.");

        Integer finalUserId = existingUserId;
        if(!requestedUsername.isBlank()) {
            Integer foundId=null; String foundRole=null;
            try(PreparedStatement p=c.prepareStatement("SELECT user_id,role FROM users WHERE username=?")){p.setString(1,requestedUsername);try(ResultSet r=p.executeQuery()){if(r.next()){foundId=r.getInt(1);foundRole=r.getString(2);}}}
            if(foundId!=null && !foundId.equals(existingUserId)) throw new IllegalArgumentException("That username is already used by another account.");
            if(foundRole!=null && !"STAFF".equals(foundRole)) throw new SecurityException("That username belongs to a non-staff account.");
            if(foundId==null) {
                try(PreparedStatement p=c.prepareStatement("INSERT INTO users(username,password_hash,role,status,full_name,email) VALUES(?,?, 'STAFF','ACTIVE',?,?)",Statement.RETURN_GENERATED_KEYS)){
                    p.setString(1,requestedUsername);p.setString(2,PasswordUtil.hash(password));p.setString(3,name);p.setString(4,email);p.executeUpdate();
                    try(ResultSet k=p.getGeneratedKeys()){k.next();finalUserId=k.getInt(1);}
                }
            } else {
                String uq = password.isBlank()
                        ? "UPDATE users SET full_name=?,email=?,role='STAFF',status='ACTIVE' WHERE user_id=?"
                        : "UPDATE users SET password_hash=?,full_name=?,email=?,role='STAFF',status='ACTIVE' WHERE user_id=?";
                try(PreparedStatement p=c.prepareStatement(uq)){
                    if(password.isBlank()){p.setString(1,name);p.setString(2,email);p.setInt(3,foundId);}
                    else {p.setString(1,PasswordUtil.hash(password));p.setString(2,name);p.setString(3,email);p.setInt(4,foundId);}
                    p.executeUpdate();
                }
            }
        }

        if(!editing) {
            try(PreparedStatement p=c.prepareStatement("INSERT INTO staff(user_id,staff_code,name,phone,email,department_id,role_id,experience_years,status) VALUES(?,?,?,?,?,?,?,?,'ACTIVE')",Statement.RETURN_GENERATED_KEYS)){
                if(finalUserId==null)p.setNull(1,Types.INTEGER);else p.setInt(1,finalUserId);p.setString(2,code);p.setString(3,name);p.setString(4,phone);p.setString(5,email);p.setInt(6,dept);p.setInt(7,role);p.setDouble(8,exp);p.executeUpdate();
                try(ResultSet k=p.getGeneratedKeys()){k.next();staffId=k.getInt(1);}
            }
        } else {
            try(PreparedStatement p=c.prepareStatement("UPDATE staff SET user_id=?,staff_code=?,name=?,phone=?,email=?,department_id=?,role_id=?,experience_years=? WHERE staff_id=?")){
                if(finalUserId==null)p.setNull(1,Types.INTEGER);else p.setInt(1,finalUserId);p.setString(2,code);p.setString(3,name);p.setString(4,phone);p.setString(5,email);p.setInt(6,dept);p.setInt(7,role);p.setDouble(8,exp);p.setInt(9,staffId);p.executeUpdate();
            }
        }

        try(PreparedStatement p=c.prepareStatement("DELETE FROM staff_skills WHERE staff_id=?")){p.setInt(1,staffId);p.executeUpdate();}
        String[] skillIds=req.getParameterValues("skill_id");
        if(skillIds!=null) try(PreparedStatement p=c.prepareStatement("INSERT INTO staff_skills(staff_id,skill_id,verification_status) VALUES(?,?,'VERIFIED')")){
            for(String v:skillIds){p.setInt(1,staffId);p.setInt(2,Integer.parseInt(v));p.addBatch();}p.executeBatch();
        }
        Audit.log(admin.id(), editing?"STAFF_UPDATED":"STAFF_CREATED", name + " ("+code+")");
    }

    private void setStatus(Connection c,HttpServletRequest req,User admin,String status)throws Exception{
        int id=Integer.parseInt(req.getParameter("staff_id"));
        Integer uid=null; String name="Staff #"+id;
        try(PreparedStatement p=c.prepareStatement("SELECT user_id,name FROM staff WHERE staff_id=?")){p.setInt(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Staff member not found.");uid=(Integer)r.getObject(1);name=r.getString(2);}}
        try(PreparedStatement p=c.prepareStatement("UPDATE staff SET status=? WHERE staff_id=?")){p.setString(1,status);p.setInt(2,id);p.executeUpdate();}
        if(uid!=null)try(PreparedStatement p=c.prepareStatement("UPDATE users SET status=? WHERE user_id=?")){p.setString(1,status);p.setInt(2,uid);p.executeUpdate();}
        Audit.log(admin.id(),"STAFF_STATUS_CHANGED",name+" -> "+status);
    }

    private Map<String,Object> loadStaff(Connection c,int id)throws Exception{
        String q="SELECT s.staff_id,s.staff_code,s.name,s.phone,s.email,s.experience_years,s.status,d.department_id,r.role_id,s.user_id,u.username FROM staff s JOIN departments d ON s.department_id=d.department_id JOIN roles r ON s.role_id=r.role_id LEFT JOIN users u ON s.user_id=u.user_id WHERE s.staff_id=?";
        try(PreparedStatement p=c.prepareStatement(q)){p.setInt(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())return null;Map<String,Object>m=new LinkedHashMap<>();m.put("id",r.getInt(1));m.put("code",r.getString(2));m.put("name",r.getString(3));m.put("phone",r.getString(4));m.put("email",r.getString(5));m.put("experience",r.getDouble(6));m.put("status",r.getString(7));m.put("departmentId",r.getInt(8));m.put("roleId",r.getInt(9));m.put("userId",r.getObject(10));m.put("username",r.getString(11));return m;}}
    }
    private Set<Integer> loadSkillIds(Connection c,int staffId)throws Exception{Set<Integer>s=new HashSet<>();try(PreparedStatement p=c.prepareStatement("SELECT skill_id FROM staff_skills WHERE staff_id=?")){p.setInt(1,staffId);try(ResultSet r=p.executeQuery()){while(r.next())s.add(r.getInt(1));}}return s;}
    private String clean(String s){return s==null?"":s.trim();}
    private List<Map<String,Object>> list(Connection c,String t,String id,String name,User u)throws Exception{
        List<Map<String,Object>>l=new ArrayList<>();List<Object>pms=new ArrayList<>();String pred="1=1";
        if(u!=null){if(Access.isSupervisor(u)&&"departments".equals(t))pred=Access.departmentPredicate(u,id,pms,c);else if(Access.isStaff(u))pred="1=0";}
        String q="SELECT "+id+","+name+" FROM "+t+" WHERE "+pred+" ORDER BY "+name;
        try(PreparedStatement p=c.prepareStatement(q)){for(int i=0;i<pms.size();i++)p.setObject(i+1,pms.get(i));try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object>m=new HashMap<>();m.put("id",r.getInt(1));m.put("name",r.getString(2));l.add(m);}}}
        return l;
    }
}
