package jp.example.filter;

import static java.util.Collections.*;

import java.sql.DriverManager;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpFilter;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingFunction;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jp.co.future.uroborosql.SqlAgent;
import jp.co.future.uroborosql.UroboroSQL;
import jp.co.future.uroborosql.config.SqlConfig;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * 自動トランザクションフィルターです。
 * <pre>
 * データベーストランザクションの一般的なテンプレート実装です。
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
	protected HikariDataSource dataSource;
	protected List<Class<?>> noRollbackExceptionList;

	/** データベース接続設定と初期データロード */
	@Override @SneakyThrows
	public void init() {
		try {
			dataSource = new HikariDataSource(new HikariConfig("/database.properties"));
			daoConfig = UroboroSQL.builder(dataSource).build();
			try (SqlAgent dao = daoConfig.agent()) {
				dao.update("create_table").count(); // ファイル実行 src/main/resources/sql/create_table.sql
			}
			String value = StringUtils.defaultString(getFilterConfig().getInitParameter("noRollbackExceptionList"));
			noRollbackExceptionList = Arrays.stream(value.trim().split("[,;\\s]+"))
					.map(ThrowingFunction.sneaky(Class::forName)).collect(Collectors.toList());
		} catch (Exception e) {
			log.error("AutoTransactionFilter 初期化エラー", e);
			throw e;
		}
	}
	
	/** データベースリソース破棄 (ドライバー解除は Tomcat 警告抑止) */
	@Override @SneakyThrows
	public void destroy() {
		dataSource.close();
		list(DriverManager.getDrivers()).forEach(ThrowingConsumer.sneaky(DriverManager::deregisterDriver));
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
				if (noRollbackExceptionList.stream().anyMatch(c -> e.getClass().isAssignableFrom(c))) {
					dao.commit(); // ↑例外でもコミットする例外クラス (Spring の noRollbackFor と同様の機能)
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
