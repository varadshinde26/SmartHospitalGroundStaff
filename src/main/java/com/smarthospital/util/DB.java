package com.smarthospital.util;
import java.sql.*;
public final class DB {
 private static final String URL = System.getenv().getOrDefault("HOSPITAL_DB_URL","jdbc:mysql://localhost:3306/smart_hospital?useSSL=false&serverTimezone=Asia/Kolkata&allowPublicKeyRetrieval=true");
 private static final String USER = System.getenv().getOrDefault("HOSPITAL_DB_USER","root");
 private static final String PASS = System.getenv().getOrDefault("HOSPITAL_DB_PASSWORD","");
 static { try { Class.forName("com.mysql.cj.jdbc.Driver"); } catch(Exception e){ throw new ExceptionInInitializerError(e); } }
 public static Connection getConnection() throws SQLException { return DriverManager.getConnection(URL,USER,PASS); }
 public static void close(AutoCloseable... c){ for(AutoCloseable x:c) if(x!=null) try{x.close();}catch(Exception ignored){} }
}
