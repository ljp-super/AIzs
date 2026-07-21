package com.yupi.yuaiagent.config;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebFilter(value = "/*", asyncSupported = true)
public class EncodingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        if (response instanceof HttpServletResponse) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            String contentType = httpResponse.getContentType();
            if (contentType != null && contentType.contains("text/event-stream")) {
                httpResponse.setContentType("text/event-stream;charset=UTF-8");
            }
        }
        chain.doFilter(request, response);
    }
}
