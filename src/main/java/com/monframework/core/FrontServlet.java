package com.monframework.core;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.File;
import java.io.IOException;

public class FrontServlet extends HttpServlet {

    @Override
    public void init() throws ServletException{
        try {
            
        } catch (Exception e) {
            throw new ServletException();
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException{
        String path = req.getServletPath();
        ServletContext context = getServletContext();
        String realUrl = context.getRealPath(path);

        if(realUrl != null){
            File file = new File(realUrl);
            if (file.exists() && file.isFile()){
                RequestDispatcher reqDisp = context.getNamedDispatcher("default");
                reqDisp.forward(req, resp);
                return ;
            }
        }

        resp.setContentType("text/plain");
        resp.getWriter().println("URL :" + path );
        resp.getWriter().println("Methode :"+req.getMethod());
    }
}
