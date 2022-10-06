package jp.example.filter;

import static jp.example.filter.AutoTransactionFilter.*;
import static jp.example.filter.RequestContextFilter.*;
import static org.apache.commons.lang3.StringUtils.*;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.codec.digest.DigestUtils;

import jodd.servlet.DispatcherUtil;
import lombok.SneakyThrows;

/**
 * ログイン認証フィルターです。
 * <pre>
 * このフィルターはセキュリティに関するもので、必須ではありません。web.xml からコメントアウトすることで無効にできます。
 * AJAX の場合は HTTP ステータス 401、それ以外の場合はログイン画面にリダイレクトします。
 * ログインに成功した場合、web.xml に指定した userEntityClass のインスタンスがセッションに "USER" として格納されます。
 * この実装では DB のユーザーテーブルに username と password カラムが必要です。
 * このフィルターより前に AutoCsrfFilter を設定することで CSRF チェックされます。
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
				// アクセス URL 保存
				session.setAttribute(LOGIN_SUCCESS_URL, DispatcherUtil.getFullUrl(req));
			}
			req.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(req, res);
		}
	}
	
	@SneakyThrows
	private void login(HttpServletRequest req, HttpServletResponse res) {
		
		String username = req.getParameter("username");
		String password = req.getParameter("password");
		
		Object user = dao()
				.query(Class.forName(getInitParameter("userEntityClass")))
				.equal("username", username)
				.equal("password", hash(req, username, password))
				.first().orElse(null);
		
		// ログイン成功
		if (user != null) {
			req.changeSessionId(); // セッション固定化攻撃対策
			HttpSession session = req.getSession();
			session.setAttribute(USER, user);
			// アクセス URL 復元
			res.sendRedirect(defaultIfEmpty((String) session.getAttribute(LOGIN_SUCCESS_URL), req.getContextPath()));
			return;
		}
		// ログイン失敗
		req.setAttribute("MESSAGE", "正しいログイン情報を入力してください。");
		req.getRequestDispatcher("/WEB-INF/jsp/login.jsp").forward(req, res);
	}
	
	/**
	 * パスワードをハッシュ化します。
	 * <pre>
	 * 開発環境など https でない場合は、開発しやすいように、引数のパスワードを平文のまま返します (DB にも平文で格納しておく)。
	 * レインボー攻撃対策としてソルト、ペッパーを付加して、SHA3_384 で 1 万回ハッシュ化します。
	 * ソルトには username を使用するため、username を変更した場合は、パスワードを再設定する必要があります。
	 * </pre>
	 * @param req HTTP リクエスト
	 * @param salt ソルト (username)
	 * @param password パスワード
	 * @return ハッシュ化したパスワード
	 */
	private String hash(HttpServletRequest req, String salt, String password) {
		if (!req.isSecure()) {
			return password;
		}
		final String papper = getClass().getSimpleName();
		String s = salt + password + papper;
		for (int i = 0; i < 10_000; i++) {
			s = DigestUtils.sha3_384Hex(s);
		}
		return s;
	}
}
