package jp.example.servlet;

import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jp.example.entity.Item;
import jp.example.form.ItemForm;

/**
 * SPA CRUD Servlet 定義クラスです。
 * <pre>
 * JSP 版と同じ機能を SPA (React や Vue などのシングルページアプリケーション) 向けの API として実装した Servlet です。
 * レスポンスに書き込み無し (returns していない) かつ例外無しの場合は、レスポンス body は空で HTTP 200 になります。
 * Servlet でスローされた例外は AutoFlashFilter で例外メッセージがレスポンスに書き込まれ HTTP 20x になります。
 * </pre>
 * @author New Gradle Project Wizard (c) Pleiades MIT
 */
public class SpaCrudServlet {

	@WebServlet("/spa/search")
	public static class SearchServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns(new ItemForm(req).findFormList());
		}
	}

	@WebServlet("/spa/count")
	public static class CountServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns("結果予想件数: " + new ItemForm(req).count() + " 件");
		}
	}
	
	@WebServlet("/spa/delete")
	public static class DeleteServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			dao().delete(new ItemForm(req).toEntity(new Item()));
		}
	}
	
	@WebServlet("/spa/detail")
	public static class DetailServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns(new ItemForm(req).findFormById());
		}
	}
	
	@WebServlet("/spa/companySelect")
	public static class CompanySelectServlet extends HttpServlet {
		protected void doGet(HttpServletRequest req, HttpServletResponse res) {
			returns(new ItemForm().getCompanySelectOptions());
		}
	}

	@WebServlet("/spa/validate")
	public static class ValidateServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			new ItemForm(req).validate(req);
		}
	}
	
	@WebServlet("/spa/insert")
	public static class InsertServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			ItemForm form = new ItemForm(req).validate(req);
			dao().insert(form.toEntity(new Item()));
		}
	}
	
	@WebServlet("/spa/update")
	public static class UpdateServlet extends HttpServlet {
		protected void doPost(HttpServletRequest req, HttpServletResponse res) {
			ItemForm form = new ItemForm(req).validate(req);
			dao().update(form.toEntity(form.findEntityById()));
		}
	}
}
