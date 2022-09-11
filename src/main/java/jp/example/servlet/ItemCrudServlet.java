package jp.example.servlet;

import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jodd.servlet.DispatcherUtil;
import jp.example.dto.Item;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Servlet JSP CRUD Servlet 定義クラスです。
 * <pre>
 * 検索一覧、登録、修正、削除画面のシンプルな Servlet パターンサンプル。
 * IllegalStateException をスローすると、アプリエラーとしてリクエスト属性 MESSAGE にセットされ、表示中のページにフォワードされます。
 * 以下のフィルタークラスの static メソッドを static インポートして使用できます。
 * forward、redirect、returns は条件分岐で呼び分ける場合でも、Servlet 内の処理はそこで終了するため、return 不要です。
 * 
 * AutoFlashFilter
 *  
 *   forward(jsp)   フォワードのショートカットメソッド (入力エラー時の戻り先として保存、CSRF 兼同期トークン自動埋め込み)
 *   redirect(url)  リダイレクトのショートカットメソッド (自動フラッシュにより、リダイレクト先でリクエスト属性がそのまま使用可能)
 *   returns(obj)   REST API などの戻り値として Java オブジェクトを JSON 文字列などに変換してクライアントに返却
 *   valid(〜)      条件とエラーメッセージを指定して、pplicationException をスローするためのショートカットメソッド
 *   $(name)        JSP EL のようにリクエスト、セッション、アプリケーションスコープから、最初に見つかった属性値を取得 (キャスト不要)
 * 
 * AutoTransactionFilter
 *  
 *   dao()          汎用 DAO トランザクションマネージャー取得 (正常時は自動コミット、ロールバックしたい場合は例外スロー)
 *   
 * </pre>
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
@Slf4j
public class ItemCrudServlet {

	/** CRUD の R: Read 検索 Servlet */
	@WebServlet("/item/list")
	public static class ListServlet extends HttpServlet {
		
		/** 検索一覧画面の表示 */
		@Override @SneakyThrows
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			
			 // 検索 SQL (2WaySQL OGNL)
			 // https://future-architect.github.io/uroborosql-doc/background/#条件分岐-if-elif-else-end
			String sql = """
					SELECT * FROM item
					WHERE 1 = 1
						/*IF SF.isNotBlank(name)*/
							AND name LIKE /*SF.contains(name)*/'Pro' escape /*#ESC_CHAR*/'$'
						/*END*/
						/*IF SF.isNotBlank(releaseDate)*/
							AND release_date = /*releaseDate*/'2022-09-11'
						/*END*/
				""";
			
			List<Item> list = dao().queryWith(sql).paramBean(new Item(req)).collect(Item.class);
			log.debug("SELECT 結果: {} 件", list.size());
			req.setAttribute("itemList", list);
			req.getSession().setAttribute("listQueryUrl", DispatcherUtil.getFullUrl(req));
			forward("list.jsp");
		}
	}
	
	/** CRUD の C: Create 登録 Servlet */
	@WebServlet("/item/create")
	public static class CreateServlet extends HttpServlet {
		
		/** 一覧画面の新規登録ボタン → 登録画面の表示 */
		@Override @SneakyThrows
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			forward("detail.jsp");
		}
		
		/** 登録画面の登録ボタン → 一覧画面へリダイレクト (PRG パターン: リロードによる二重登録抑止) */
		@Override @SneakyThrows
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			dao().insert(new Item(req).validate());
			req.setAttribute(MESSAGE, "登録しました。");
			redirect($("listQueryUrl"));
		}
	}

	/** CRUD の U: Update 変更 Servlet */
	@WebServlet("/item/update")
	public static class UpdateServlet extends HttpServlet {
		
		/** 一覧画面の変更ボタン → 変更画面の表示 */
		@Override @SneakyThrows
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			Item item = dao().find(Item.class, new Item(req).id).orElseThrow(() -> new Error("存在しません。"));
			req.setAttribute("item", item);
			forward("detail.jsp");
		}
		
		/** 変更画面の更新ボタン → 一覧画面へリダイレクト (PRG パターン: リロードによる二回更新抑止) */
		@Override @SneakyThrows
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			dao().update(new Item(req).validate());
			req.setAttribute(MESSAGE, "更新しました。");
			redirect($("listQueryUrl"));
		}
	}

	/** CRUD の D: Delete 削除 Servlet */
	@WebServlet("/item/delete")
	public static class DeleteServlet extends HttpServlet {
		
		/** 一覧画面の削除ボタン → 一覧画面へリダイレクト (リロードによる二回削除抑止) */
		@Override @SneakyThrows
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			dao().delete(new Item(req));
			req.setAttribute(MESSAGE, "削除しました。");
			redirect($("listQueryUrl"));
		}
	}
}
