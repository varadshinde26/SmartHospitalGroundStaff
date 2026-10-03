package com.smarthospital.util;
import java.sql.*;
public final class Audit { public static void log(Integer uid,String action,String desc){String q="INSERT INTO audit_logs(user_id,action,description) VALUES(?,?,?)";try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement(q)){if(uid==null)p.setNull(1,Types.INTEGER);else p.setInt(1,uid);p.setString(2,action);p.setString(3,desc);p.executeUpdate();}catch(Exception ignored){}} }
