package jp.example.filter;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import lombok.SneakyThrows;

/**
 * リクエストコンテキストフィルターです。
 * <pre>
 * </pre>
 * @author New Gradle Project Wizard (c) Pleiades MIT
 */
public class RequestContextFilter extends HttpFilter {
	
	private static final ThreadLocal<RequestContext> requestContextThreadLocal = new ThreadLocal<>();
	
	private record RequestContext (
		HttpServletRequest req,
		HttpServletResponse res
	) {};

	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		try {
			requestContextThreadLocal.set(new RequestContext(req, res));
			super.doFilter(req, res, chain);
		} finally {
			requestContextThreadLocal.remove();
		}
	}
	
	static HttpServletRequest request() {
		return requestContextThreadLocal.get().req;
	}
	
	static HttpServletResponse response() {
		return requestContextThreadLocal.get().res;
	}
	
	static void set(HttpServletRequest req, HttpServletResponse res) {
		requestContextThreadLocal.set(new RequestContext(req, res));
	}
	
	static boolean isAjax(HttpServletRequest req) {
		return "XMLHttpRequest".equals(req.getHeader("X-Requested-With")) ||
				StringUtils.contains(req.getHeader("Accept"), "/json");
	}
}
