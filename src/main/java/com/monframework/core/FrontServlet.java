package com.monframework.core;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.File;
import java.io.IOException;

@WebServlet(name = "FrontServlet", urlPatterns = {"/"}, loadOnStartup = 1)
public class FrontServlet extends HttpServlet {

    @Override
    public void init() throws ServletException{
        try {
            
        } catch (Exception e) {
            throw new ServletException();
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        service(request, response);
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        service(request, response);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException{
        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();
        
        String resourcePath = requestURI.substring(contextPath.length());
        
        try {
            java.net.URL resource = getServletContext().getResource(resourcePath);
            if (resource != null) {
                RequestDispatcher defaultServlet = getServletContext().getNamedDispatcher("default");
                if (defaultServlet != null) {
                    defaultServlet.forward(req, resp);
                    return;
                }
            }
        } catch (Exception e) {
            throw new ServletException("Erreur lors de la vérification de la ressource: " + resourcePath, e);
        }

        resp.setContentType("text/plain");
        resp.getWriter().println("URL :" + resourcePath );
        resp.getWriter().println("Methode :"+req.getMethod());
    }
}
