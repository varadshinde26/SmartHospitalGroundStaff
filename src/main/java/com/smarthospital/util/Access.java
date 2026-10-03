package com.smarthospital.util;

import com.smarthospital.model.User;
import java.sql.*;
import java.util.*;

/** Central authorization/scoping rules. Admin sees everything; supervisors see only mapped departments. */
public final class Access {
    private Access() {}
    public static boolean isAdmin(User u){ return u != null && "ADMIN".equals(u.role()); }
    public static boolean isSupervisor(User u){ return u != null && "SUPERVISOR".equals(u.role()); }
    public static boolean isStaff(User u){ return u != null && "STAFF".equals(u.role()); }

    public static boolean supervisorHasDepartment(Connection c, int userId, int departmentId) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM supervisor_departments WHERE supervisor_user_id=? AND department_id=? AND active=1")){
            p.setInt(1,userId); p.setInt(2,departmentId);
            try(ResultSet r=p.executeQuery()){ return r.next(); }
        }
    }
    public static Set<Integer> departments(Connection c, User u) throws SQLException {
        Set<Integer> out=new LinkedHashSet<>();
        if(isAdmin(u)){
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT department_id FROM departments ORDER BY department_id")){while(r.next())out.add(r.getInt(1));}
        } else if(isSupervisor(u)) {
            try(PreparedStatement p=c.prepareStatement("SELECT department_id FROM supervisor_departments WHERE supervisor_user_id=? AND active=1 ORDER BY department_id")){
                p.setInt(1,u.id());try(ResultSet r=p.executeQuery()){while(r.next())out.add(r.getInt(1));}
            }
        }
        return out;
    }
    public static String departmentPredicate(User u,String column, List<Object> params, Connection c) throws SQLException {
        if(isAdmin(u)) return "1=1";
        Set<Integer> ids=departments(c,u);
        if(ids.isEmpty()) return "1=0";
        StringBuilder b=new StringBuilder(column+" IN ("); boolean first=true;
        for(Integer id:ids){if(!first)b.append(',');first=false;b.append('?');params.add(id);} return b.append(')').toString();
    }
    public static int ownStaffId(Connection c,int userId)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT staff_id FROM staff WHERE user_id=? AND status='ACTIVE'")){p.setInt(1,userId);try(ResultSet r=p.executeQuery()){if(r.next())return r.getInt(1);}}
        throw new IllegalArgumentException("Staff profile not found.");
    }
    public static boolean canAccessStaff(Connection c,User u,int staffId)throws SQLException{
        if(isAdmin(u)) return true;
        if(isStaff(u)) return ownStaffId(c,u.id())==staffId;
        try(PreparedStatement p=c.prepareStatement("SELECT department_id FROM staff WHERE staff_id=? AND status='ACTIVE'")){p.setInt(1,staffId);try(ResultSet r=p.executeQuery()){return r.next() && supervisorHasDepartment(c,u.id(),r.getInt(1));}}
    }
    public static boolean canAccessDuty(Connection c,User u,int dutyId)throws SQLException{
        if(isAdmin(u)) return true;
        if(isStaff(u)) return false;
        try(PreparedStatement p=c.prepareStatement("SELECT department_id FROM duties WHERE duty_id=?")){p.setInt(1,dutyId);try(ResultSet r=p.executeQuery()){return r.next() && supervisorHasDepartment(c,u.id(),r.getInt(1));}}
    }
}
