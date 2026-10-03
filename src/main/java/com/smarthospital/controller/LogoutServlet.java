package com.smarthospital.controller; import javax.servlet.annotation.*; import javax.servlet.http.*; import java.io.*;
@WebServlet("/logout") public class LogoutServlet extends HttpServlet { protected void doGet(HttpServletRequest r,HttpServletResponse s)throws IOException{if(r.getSession(false)!=null)r.getSession(false).invalidate();s.sendRedirect(r.getContextPath()+"/login.jsp");}}
