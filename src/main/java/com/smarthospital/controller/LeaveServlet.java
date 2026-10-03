package com.smarthospital.controller;
import com.smarthospital.model.User;import com.smarthospital.util.*;import javax.servlet.*;import javax.servlet.annotation.*;import javax.servlet.http.*;import java.io.*;import java.sql.*;import java.time.LocalDate;import java.util.*;
@WebServlet("/leave") public class LeaveServlet extends HttpServlet{
 protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws ServletException,IOException{
  User u=(User)req.getSession().getAttribute("user");
  try(Connection c=DB.getConnection()){
   List<Map<String,Object>> rows=new ArrayList<>();
   String scope=Access.isStaff(u)?"s.user_id=?":Access.isAdmin(u)?"1=1":"s.department_id IN (SELECT department_id FROM supervisor_departments WHERE supervisor_user_id=? AND active=1)";
   String q="SELECT l.leave_id,s.name,s.staff_code,l.start_date,l.end_date,l.reason,l.status FROM leaves l JOIN staff s ON l.staff_id=s.staff_id WHERE "+scope+" ORDER BY l.created_at DESC";
   try(PreparedStatement p=c.prepareStatement(q)){if(!Access.isAdmin(u))p.setInt(1,u.id());try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object>m=new HashMap<>();for(int i=1;i<=7;i++)m.put("c"+i,r.getObject(i));rows.add(m);}}}
   req.setAttribute("rows",rows);req.getRequestDispatcher("/leave.jsp").forward(req,resp);
  }catch(Exception e){throw new ServletException(e);}
 }
 protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException{
  User u=(User)req.getSession().getAttribute("user");String action=req.getParameter("action");
  try(Connection c=DB.getConnection()){
   if("request".equals(action)){
    int staff=Access.ownStaffId(c,u.id());LocalDate start=LocalDate.parse(req.getParameter("start_date")),end=LocalDate.parse(req.getParameter("end_date"));
    if(end.isBefore(start))throw new IllegalArgumentException("End date cannot be before start date.");String reason=req.getParameter("reason");if(reason==null||reason.trim().isEmpty())throw new IllegalArgumentException("Leave reason is required.");
    try(PreparedStatement p=c.prepareStatement("INSERT INTO leaves(staff_id,start_date,end_date,reason) VALUES(?,?,?,?)")){p.setInt(1,staff);p.setDate(2,java.sql.Date.valueOf(start));p.setDate(3,java.sql.Date.valueOf(end));p.setString(4,reason.trim());p.executeUpdate();}
    notifyManagers(c,staff);
   }else if("approve".equals(action)||"reject".equals(action)){
    if(Access.isStaff(u))throw new SecurityException("Staff cannot approve leave.");int id=Integer.parseInt(req.getParameter("leave_id"));if(Access.isSupervisor(u)&&!leaveInScope(c,u,id))throw new SecurityException("This leave belongs to another department.");
    String status="approve".equals(action)?"APPROVED":"REJECTED";try(PreparedStatement p=c.prepareStatement("UPDATE leaves SET status=?,approved_by=? WHERE leave_id=?")){p.setString(1,status);p.setInt(2,u.id());p.setInt(3,id);if(p.executeUpdate()==0)throw new IllegalArgumentException("Leave request not found.");}
   }else throw new IllegalArgumentException("Unsupported leave action.");
   Audit.log(u.id(),"LEAVE_UPDATED",action);resp.sendRedirect(req.getContextPath()+"/leave");
  }catch(Exception e){resp.sendError(400,e.getMessage());}
 }
 private void notifyManagers(Connection c,int staffId)throws Exception{
  String msg="New leave request from staff #"+staffId+". Please review.";
  try(PreparedStatement p=c.prepareStatement("INSERT INTO notifications(user_id,message,type) SELECT u.user_id,?, 'LEAVE_REQUEST' FROM users u JOIN supervisor_departments sd ON u.user_id=sd.supervisor_user_id WHERE u.role='SUPERVISOR' AND u.status='ACTIVE' AND sd.active=1 AND sd.department_id=(SELECT department_id FROM staff WHERE staff_id=?) UNION ALL SELECT u.user_id,?, 'LEAVE_REQUEST' FROM users u WHERE u.role='ADMIN' AND u.status='ACTIVE'")){p.setString(1,msg);p.setInt(2,staffId);p.setString(3,msg);p.executeUpdate();}
 }
 private boolean leaveInScope(Connection c,User u,int id)throws Exception{try(PreparedStatement p=c.prepareStatement("SELECT s.department_id FROM leaves l JOIN staff s ON l.staff_id=s.staff_id WHERE l.leave_id=?")){p.setInt(1,id);try(ResultSet r=p.executeQuery()){return r.next()&&Access.supervisorHasDepartment(c,u.id(),r.getInt(1));}}}
}
