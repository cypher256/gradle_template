package jp.example.servlet;

import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jodd.servlet.DispatcherUtil;
import jp.example.entity.Item;
import jp.example.form.ItemForm;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * JSP CRUD Servlet 定義クラスです。
 * <pre>
 * 検索一覧、登録、修正、削除のシンプルな Servlet パターンサンプル。
 * 以下のフィルタークラスの static メソッドを static インポート (Ctrl/Cmd + Shift + m) して使用できます。
 * forward、redirect、returns は条件分岐で呼び分ける場合でも、Servlet 内の処理はそこで終了するため、return 不要です。
 * 
 * AutoFlashFilter
 *  
 *   forward(jsp)   フォワードのショートカット (入力エラー時の戻り先として保存、CSRF 兼同期トークン自動埋め込み)
 *   redirect(url)  リダイレクトのショートカット (自動フラッシュにより、リダイレクト先でリクエスト属性がそのまま使用可能)
 *   returns(obj)   REST API などの戻り値として Java オブジェクトを JSON 文字列などに変換してクライアントに返却
 *   valid(〜)      条件とエラーメッセージを指定して、pplicationException をスローするためのショートカットメソッド
 *   $("name")      JSP EL のようにリクエスト、セッションなどのスコープから、最初に見つかった属性値を取得 (キャスト不要)
 * 
 * AutoTransactionFilter
 *  
 *   dao()          汎用 DAO トランザクションマネージャー取得 (正常時は自動コミット、ロールバックしたい場合は例外スロー)
 *   
 * </pre>
 * @author New Gradle Project Wizard (c) Pleiades MIT
 */
@Slf4j
public class JspCrudServlet {

	/** CRUD の R: Read 検索 Servlet */
	@WebServlet("/item/list")
	public static class ListServlet extends HttpServlet {
		
		/** 検索一覧画面の表示 */
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			log.debug("検索して list.jsp にフォワード");
			req.setAttribute("formList", new ItemForm(req).findFormList());
			req.getSession().setAttribute("lastQueryUrl", DispatcherUtil.getFullUrl(req)); // PRG リダイレクト先保存
			forward("list.jsp");
		}
	}
	
	/** CRUD の C: Create 登録 Servlet */
	@WebServlet("/item/create")
	public static class CreateServlet extends HttpServlet {
		
		/** 一覧画面の新規登録ボタン → 登録画面の表示 */
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			req.setAttribute("form", new ItemForm());
			forward("detail.jsp");
		}
		
		/** 登録画面の登録ボタン → 一覧画面へリダイレクト (PRG パターン: リロードによる多重送信抑止) */
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			ItemForm form = new ItemForm(req).validate(req);
			dao().insert(form.toEntity(new Item()));
			redirect($("lastQueryUrl"), "ℹ️ 登録しました。");
		}
	}
	
	/** CRUD の U: Update 変更 Servlet */
	@WebServlet("/item/update")
	public static class UpdateServlet extends HttpServlet {
		
		/** 一覧画面の変更ボタン → 変更画面の表示 */
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			req.setAttribute("form", new ItemForm(req).findFormById());
			forward("detail.jsp");
		}
		
		/** 変更画面の更新ボタン → 一覧画面へリダイレクト (PRG パターン: リロードによる多重送信抑止) */
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			ItemForm form = new ItemForm(req).validate(req);
			dao().update(form.toEntity(form.findEntityById()));
			redirect($("lastQueryUrl"), "ℹ️ 更新しました。");
		}
	}
	
	/** CRUD の D: Delete 削除 Servlet */
	@WebServlet("/item/delete")
	public static class DeleteServlet extends HttpServlet {
		
		/** 一覧画面の削除ボタン → 一覧画面へリダイレクト (PRG パターン: リロードによる多重送信抑止) */
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			dao().delete(new ItemForm(req).toEntity(new Item()));
			redirect($("lastQueryUrl"), "️ℹ️ 削除しました。");
		}
	}

	/** REST API Servlet */
	@WebServlet("/item/api")
	public static class RestServlet extends HttpServlet {
		
		/** 検索画面の検索文字列 onkeyup 時の検索結果件数取得 API */
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			@Data class SearchResult {
				ItemForm condition = new ItemForm(req); // クライアントで件数以外は使用しないが json 返却例としてセット
				long count = condition.count();
			}
			returns(new SearchResult()); // レスポンス: json 結果件数情報 (例外発生時は text エラーメッセージ文字列)
		}
		
		/** 登録、変更画面の onkeyup、onchange 時の入力チェック API */
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			new ItemForm(req).validate(req); // レスポンス: 正常時はなし、例外スロー時は text エラーメッセージ文字列
		}
	}
}
