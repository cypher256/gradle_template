package jp.example.filter;

import static jp.example.filter.AutoTransactionFilter.*;
import static jp.example.filter.RequestContextFilter.*;
import static org.apache.commons.lang3.StringUtils.*;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import jodd.servlet.DispatcherUtil;
import lombok.SneakyThrows;

/**
 * ログイン認証フィルターです。
 * <pre>
 * このフィルターはセキュリティに関するもので、必須ではありません。web.xml からコメントアウトすることで無効にできます。
 * AJAX の場合は HTTP ステータス 401、それ以外の場合はログイン画面にリダイレクトします。
 * </pre>
 * @author New Gradle Project Wizard (c) Pleiades MIT
 */
public class AuthFilter extends HttpFilter {
	
	private static final String USER = "USER";
	private static final String LOGIN_SUCCESS_URL = "LOGIN_SUCCESS_URL";
	
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		String path = req.getRequestURI().substring(req.getContextPath().length());
		HttpSession session = req.getSession();

		if (path.equals("/logout")) {
			session.invalidate();
			res.sendRedirect(req.getContextPath());
			return;
		}
		if (path.equals("/login") && req.getMethod().equals("POST")) {
			login(req, res);
			return;
		}
		
		// 認証済み or 静的コンテンツ
		Object user = session.getAttribute(USER);
		if (user != null || path.equals("/static")) {
			super.doFilter(req, res, chain);
			return;
		}
		
		// 未認証 (AJAX: HTTP 401、画面: ログイン画面にフォワード)
		if (isAjax(req)) {
			res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
		} else {
			if (req.getMethod().equals("GET")) {
				session.setAttribute(LOGIN_SUCCESS_URL, DispatcherUtil.getFullUrl(req));
			}
			req.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(req, res);
		}
	}
	
	@SneakyThrows
	private void login(HttpServletRequest req, HttpServletResponse res) {
		
		Object user = dao()
				.query(Class.forName(getInitParameter("userEntityClass")))
				.equal("username", req.getParameter("username"))
				.equal("password", req.getParameter("password")) // TODO isSecure ハッシュ化が必要
				.first().orElse(null);
		
		// ログイン成功
		if (user != null) {
			req.changeSessionId(); // セッション固定化攻撃対策
			HttpSession session = req.getSession();
			session.setAttribute(USER, user);
			res.sendRedirect(defaultIfEmpty((String) session.getAttribute(LOGIN_SUCCESS_URL), req.getContextPath()));
			return;
		}
		// ログイン失敗
		req.setAttribute("MESSAGE", "正しいログイン情報を入力してください。");
		req.getRequestDispatcher("/login.html").forward(req, res);
	}
}
