package jp.example.filter;

import static java.util.stream.Collectors.*;
import static org.apache.commons.lang3.function.Failable.*;

import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.naming.InitialContext;
import javax.servlet.FilterChain;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;

import jp.co.future.uroborosql.SqlAgent;
import jp.co.future.uroborosql.UroboroSQL;
import jp.co.future.uroborosql.config.SqlConfig;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 自動トランザクションフィルターです。
 * <pre>
 * データベーストランザクションの一般的なテンプレート実装です。
 * Servlet の処理が正常に終了した場合はコミット、例外が発生した場合は自動的にロールバックされます。
 * このフィルターでは uroboroSQL を使用して、データベースの初期データロード、トランザクションを制御します。
 * </pre>
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
@Slf4j
public class AutoTransactionFilter extends HttpFilter {
	
	//-------------------------------------------------------------------------
	// アプリから使用する public static メソッド
	//-------------------------------------------------------------------------

	/**
	 * 汎用 DAO トランザクションマネージャーを取得します。
	 * <pre>
	 * 自動採番の主キーを持つテーブルは、id などのエンティティに関するアノテーションは不要です。
	 * スネークケース、キャメルケースは自動変換されます。ただし、バインドパラメータ名は変換されません。
	 * <a href="https://future-architect.github.io/uroborosql-doc/why_uroborosql/"
	 * >GitHub: uroboroSQL (ウロボロスキュール)</a>
	 * </pre>
	 * @return SqlAgent
	 */
	public static SqlAgent dao() {
		return daoThreadLocal.get();
	}
	
	//-------------------------------------------------------------------------
	// Servlet フィルター処理
	//-------------------------------------------------------------------------
	
	protected static final ThreadLocal<SqlAgent> daoThreadLocal = new ThreadLocal<>();
	protected SqlConfig daoConfig;
	protected List<Class<?>> noRollbackExceptionList;

	/** データベース接続設定と初期データロード */
	@Override @SneakyThrows
	public void init() {
		try {
			DataSource dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/main");
			daoConfig = UroboroSQL.builder(dataSource).build();
			try (SqlAgent dao = daoConfig.agent()) {
				dao.update("create_table").count(); // ファイル実行 src/main/resources/sql/create_table.sql
			}
			String param = getFilterConfig().getInitParameter("noRollbackExceptionList");
			noRollbackExceptionList = Arrays.stream(param.split("[,;\\s]+"))
					.filter(StringUtils::isNotEmpty)
					.map(asFunction(Class::forName)).collect(toList());
		} catch (Exception e) {
			log.error("AutoTransactionFilter 初期化エラー", e);
			throw e;
		}
	}
	
	/** データベースリソース破棄 (ドライバー解除は Tomcat 警告抑止) */
	@Override @SneakyThrows
	public void destroy() {
		Collections.list(DriverManager.getDrivers()).forEach(asConsumer(DriverManager::deregisterDriver));
	}
	
	/** トランザクション開始、コミット、ロールバック */
	@Override @SneakyThrows
	protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
		
		// html css js などを除外
		if (req.getRequestURI().contains(".")) {
			super.doFilter(req, res, chain);
			return;
		}
		
		// トランザクション制御ブロック
		try (SqlAgent dao = daoConfig.agent()) {
			try {
				daoThreadLocal.set(dao);
				super.doFilter(req, res, chain); // Servlet 呼び出し
				dao.commit();
				
			} catch (Throwable e) {
				if (noRollbackExceptionList.stream().anyMatch(def -> def.isInstance(e))) {
					dao.commit(); // ↑例外でもコミットする例外クラス (web.xml の init-param 設定)
				} else {
					dao.rollback();
				}
				throw e;
				
			} finally {
				daoThreadLocal.remove();
			}
		}
	}
}
