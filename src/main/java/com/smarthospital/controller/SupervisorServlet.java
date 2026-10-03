package com.smarthospital.controller;

import com.smarthospital.model.User;
import com.smarthospital.util.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.servlet.*;
import java.io.*;
import java.sql.*;
import java.util.*;

@WebServlet("/supervisors")
public class SupervisorServlet extends HttpServlet {
 protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
  User u=(User)req.getSession().getAttribute("user"); if(!Access.isAdmin(u)){resp.sendError(403,"Administrator access required");return;}
  try(Connection c=DB.getConnection()){
   List<Map<String,Object>> rows=new ArrayList<>();
   String q="SELECT u.user_id,u.username,u.full_name,u.email,u.status,COALESCE(GROUP_CONCAT(d.department_name ORDER BY d.department_name SEPARATOR ', '),'Unassigned') departments, COALESCE(GROUP_CONCAT(d.department_id ORDER BY d.department_id SEPARATOR ','),'') department_ids " +
            "FROM users u LEFT JOIN supervisor_departments sd ON u.user_id=sd.supervisor_user_id AND sd.active=1 LEFT JOIN departments d ON sd.department_id=d.department_id " +
            "WHERE u.role='SUPERVISOR' GROUP BY u.user_id ORDER BY u.full_name,u.username";
   try(Statement s=c.createStatement();ResultSet r=s.executeQuery(q)){while(r.next()){Map<String,Object>m=new LinkedHashMap<>();for(int i=1;i<=7;i++)m.put("c"+i,r.getObject(i));rows.add(m);}}
   req.setAttribute("rows",rows); req.setAttribute("departments",listDepartments(c));
   req.getRequestDispatcher("/supervisors.jsp").forward(req,resp);
  }catch(Exception e){throw new ServletException(e);}
 }
 protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException{
  User u=(User)req.getSession().getAttribute("user"); if(!Access.isAdmin(u)){resp.sendError(403);return;}
  String action=req.getParameter("action");
  try(Connection c=DB.getConnection()){
   c.setAutoCommit(false);
   if("save".equals(action)){
    String id=req.getParameter("user_id"), username=clean(req.getParameter("username")), name=clean(req.getParameter("full_name")), email=clean(req.getParameter("email"));
    String password=req.getParameter("password"); if(username.isBlank()||name.isBlank()||email.isBlank())throw new IllegalArgumentException("Name, username and email are required.");
    int userId;
    if(id==null||id.isBlank()){
     if(password==null||password.length()<8)throw new IllegalArgumentException("Password must be at least 8 characters.");
     try(PreparedStatement p=c.prepareStatement("INSERT INTO users(username,password_hash,role,status,full_name,email) VALUES(?,?, 'SUPERVISOR','ACTIVE',?,?)",Statement.RETURN_GENERATED_KEYS)){
      p.setString(1,username);p.setString(2,PasswordUtil.hash(password));p.setString(3,name);p.setString(4,email);p.executeUpdate();try(ResultSet r=p.getGeneratedKeys()){r.next();userId=r.getInt(1);}
     }
    }else{
     userId=Integer.parseInt(id);
     if(password!=null&&!password.isBlank()){
      if(password.length()<8)throw new IllegalArgumentException("Password must be at least 8 characters.");
      try(PreparedStatement p=c.prepareStatement("UPDATE users SET username=?,full_name=?,email=?,password_hash=?,role='SUPERVISOR' WHERE user_id=? AND role='SUPERVISOR'")){p.setString(1,username);p.setString(2,name);p.setString(3,email);p.setString(4,PasswordUtil.hash(password));p.setInt(5,userId);p.executeUpdate();}
     }else try(PreparedStatement p=c.prepareStatement("UPDATE users SET username=?,full_name=?,email=?,role='SUPERVISOR' WHERE user_id=? AND role='SUPERVISOR'")){p.setString(1,username);p.setString(2,name);p.setString(3,email);p.setInt(4,userId);p.executeUpdate();}
     try(PreparedStatement p=c.prepareStatement("DELETE FROM supervisor_departments WHERE supervisor_user_id=?")){p.setInt(1,userId);p.executeUpdate();}
    }
    String[] deps=req.getParameterValues("department_id"); if(deps==null||deps.length==0)throw new IllegalArgumentException("Assign at least one department.");
    try(PreparedStatement p=c.prepareStatement("INSERT INTO supervisor_departments(supervisor_user_id,department_id,active) VALUES(?,?,1)")){for(String d:deps){p.setInt(1,userId);p.setInt(2,Integer.parseInt(d));p.addBatch();}p.executeBatch();}
    Audit.log(u.id(),"SUPERVISOR_SAVED",name); c.commit();
   } else if("status".equals(action)){
    int id=Integer.parseInt(req.getParameter("user_id")); String status="ACTIVE".equals(req.getParameter("status"))?"INACTIVE":"ACTIVE";
    if(id==u.id())throw new IllegalArgumentException("You cannot deactivate your own administrator account.");
    try(PreparedStatement p=c.prepareStatement("UPDATE users SET status=? WHERE user_id=? AND role='SUPERVISOR'")){p.setString(1,status);p.setInt(2,id);if(p.executeUpdate()==0)throw new IllegalArgumentException("Supervisor not found.");}
    Audit.log(u.id(),"SUPERVISOR_STATUS",id+" -> "+status); c.commit();
   } else if("reset".equals(action)){
    int id=Integer.parseInt(req.getParameter("user_id"));String pw=req.getParameter("password");if(pw==null||pw.length()<8)throw new IllegalArgumentException("New password must be at least 8 characters.");
    try(PreparedStatement p=c.prepareStatement("UPDATE users SET password_hash=? WHERE user_id=? AND role='SUPERVISOR'")){p.setString(1,PasswordUtil.hash(pw));p.setInt(2,id);p.executeUpdate();} Audit.log(u.id(),"SUPERVISOR_PASSWORD_RESET","Supervisor #"+id);c.commit();
   } else throw new IllegalArgumentException("Unsupported action.");
   resp.sendRedirect(req.getContextPath()+"/supervisors");
  }catch(Exception e){resp.sendError(400,e.getMessage());}
 }
 private String clean(String s){return s==null?"":s.trim();}
 private List<Map<String,Object>> listDepartments(Connection c)throws Exception{List<Map<String,Object>>l=new ArrayList<>();try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT department_id,department_name FROM departments ORDER BY department_name")){while(r.next()){Map<String,Object>m=new HashMap<>();m.put("id",r.getInt(1));m.put("name",r.getString(2));l.add(m);}}return l;}
}
