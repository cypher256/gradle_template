package jp.example;

import static jp.example.SingleTierController.*;

import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jodd.servlet.DispatcherUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Servlet JSP CRUD サンプルクラスです。
 * @author Pleiades All in One New Gradle Project Wizard (EPL)
 */
@WebServlet("")
@Slf4j
public class ItemCrudServlet extends HttpServlet {
	
	/** CRUD の R: Read (SELECT) 検索して一覧画面を表示 */
	@Override @SneakyThrows
	protected void doGet(HttpServletRequest req, HttpServletResponse res) {

		// 2WaySQL OGNL - https://future-architect.github.io/uroborosql-doc/background/#条件分岐-if-elif-else-end
		List<Item> list = dao().queryWith("""
				SELECT * FROM item
				WHERE 1 = 1
					/*IF SF.isNotBlank(name)*/ 
						AND name LIKE /*SF.contains(name)*/'Pro' escape /*#ESC_CHAR*/'$' 
					/*END*/
					/*IF SF.isNotBlank(releaseDate)*/ 
						AND release_date = /*releaseDate*/'2022-09-11'
					/*END*/
			""").paramBean(new Item(req)).collect(Item.class);
		
		log.debug("SELECT 結果: {} 件 - {}", list.size(), list.stream().findFirst().orElse(null));
		req.setAttribute("itemList", list);
		req.getSession().setAttribute("searchUrl", DispatcherUtil.getFullUrl(req));
		forward("list.jsp");
	}

	/** CRUD の C: Create (INSERT) 登録 Servlet */
	@WebServlet("/create")
	public static class CreateServlet extends HttpServlet {
		
		/** 一覧画面の新規登録ボタン → 登録画面の表示 */
		@Override @SneakyThrows
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			forward("detail.jsp");
		}
		
		/** 登録画面の登録ボタン → 一覧画面へリダイレクト (PRG パターン) */
		@Override @SneakyThrows
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			dao().insert(new Item(req).validate());
			req.setAttribute(MESSAGE, "登録しました。");
			redirect(req.getSession().getAttribute("searchUrl"));
		}
	}

	/** CRUD の U: Update (UPDATE) 変更 Servlet */
	@WebServlet("/update")
	public static class UpdateServlet extends HttpServlet {
		
		/** 一覧画面の変更ボタン → 変更画面の表示 */
		@Override @SneakyThrows
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			Item item = dao().find(Item.class, new Item(req).id).orElseThrow(() -> new Error("存在しません。"));
			req.setAttribute("item", item);
			forward("detail.jsp");
		}
		
		/** 変更画面の更新ボタン → 一覧画面へリダイレクト (PRG パターン) */
		@Override @SneakyThrows
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			dao().update(new Item(req).validate());
			req.setAttribute(MESSAGE, "更新しました。");
			redirect(req.getSession().getAttribute("searchUrl"));
		}
	}

	/** CRUD の D: Delete (DELETE) 削除 Servlet */
	@WebServlet("/delete")
	public static class DeleteServlet extends HttpServlet {
		
		/** 一覧画面の削除ボタン → 一覧画面へリダイレクト (F5 対策のリダイレクト) */
		@Override @SneakyThrows
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			dao().delete(new Item(req));
			req.setAttribute(MESSAGE, "削除しました。");
			redirect(req.getSession().getAttribute("searchUrl"));
		}
	}

	/** 入力チェック API Servlet */
	@WebServlet("/validate")
	public static class ValidateApiServlet extends HttpServlet {
		
		/** 登録、変更画面の入力中エラーメッセージ → エラーがあればメッセージ文字列返却 */
		@Override @SneakyThrows
		protected void doPut(HttpServletRequest req, HttpServletResponse res) {
			try {
				new Item(req).validate();
				res.getWriter().write("");
			} catch (Exception e) {
				res.getWriter().write(e.getMessage());
			}
		}
	}
}
