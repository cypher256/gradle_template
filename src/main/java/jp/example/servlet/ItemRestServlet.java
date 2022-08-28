package jp.example.servlet;

import static jp.example.filter.AutoTransactionFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import jp.example.dto.Item;
import lombok.Data;
import lombok.SneakyThrows;

/**
 * Servlet REST Servlet サンプルクラスです。
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
@WebServlet("/api")
public class ItemRestServlet extends HttpServlet {
	
	/** Java オブジェクトを JSON 文字列に変換するための ObjectMapper */
	private final ObjectMapper json = new ObjectMapper();
	
	/** 
	 * 検索画面でのリアルタイム検索結果件数取得 API です。<br>
	 * クライアントへ json で結果件数情報を返します。
	 */
	@Override @SneakyThrows
	protected void doGet(HttpServletRequest req, HttpServletResponse res) {
		
		String sql = """
				SELECT COUNT(*) 
				FROM item
				WHERE 1 = 1
					/*IF SF.isNotBlank(name)*/ 
						AND name LIKE /*SF.contains(name)*/'Pro' escape /*#ESC_CHAR*/'$' 
					/*END*/
					/*IF SF.isNotBlank(releaseDate)*/ 
						AND release_date = /*releaseDate*/'2022-09-11'
					/*END*/
			""";
		
		@Data
		class SearchResult {
			Item condition = new Item(req); // クライアントで件数以外は使用しないが json サンプルのため
			int count = dao().queryWith(sql).paramBean(condition).one(int.class);
		}
		res.getWriter().print(json.writeValueAsString(new SearchResult()));
	}
	
	/**
	 * 登録、変更画面のリアルタイム入力チェック API です。<br>
	 * validate でスローされた例外は、AutoFlashFilter でキャッチされ例外メッセージを、文字列として返却します。<br>
	 * (更新系ではないため post はふさわしくないがサンプルのため。post のためトークンもチェックされる。) 
	 */
	@Override @SneakyThrows
	protected void doPost(HttpServletRequest req, HttpServletResponse res) {
		new Item(req).validate();
	}
}
