package com.smarthospital.controller;
import javax.servlet.*;import javax.servlet.http.*;import javax.servlet.annotation.*;import java.io.*;
@WebFilter(urlPatterns={"/dashboard","/staff","/duties","/allocation","/supervisors","/leave","/attendance","/notifications","/reports","/documents","/document-download","/my-duties"})
public class AuthFilter implements Filter {public void doFilter(ServletRequest a,ServletResponse b,FilterChain f)throws IOException,ServletException{HttpServletRequest r=(HttpServletRequest)a;HttpSession s=r.getSession(false);if(s==null||s.getAttribute("user")==null){((HttpServletResponse)b).sendRedirect(r.getContextPath()+"/login.jsp");return;}f.doFilter(a,b);}}
