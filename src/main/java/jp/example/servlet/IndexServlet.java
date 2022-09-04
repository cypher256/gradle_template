package jp.example.servlet;

import static java.lang.System.*;
import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jp.example.dto.Item;

/**
 * コンテキストルートのトップ画面を表示するインデックス Servlet クラスです。
 * @author New Gradle Project Wizard (c) https://opensource.org/licenses/mit-license.php
 */
@WebServlet("")
public class IndexServlet extends HttpServlet {
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) {
		
		System.out.println("----- [DEBUG] item テーブル全件 SELECT (クラス指定) -----");
		dao().query(Item.class).stream().forEach(out::println);
		
		System.out.println("----- [DEBUG] users テーブル全件 SELECT (SQL 文指定) -----");
		dao().queryWith("SELECT * from users").stream().forEach(out::println);
		
		// 必要に応じて、トップ画面に表示するコンテンツなどを取得
		forward("index.jsp");
	}
}
