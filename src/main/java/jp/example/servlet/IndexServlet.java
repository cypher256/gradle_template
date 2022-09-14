package jp.example.servlet;

import static java.lang.System.*;
import static jp.example.filter.AutoFlashFilter.*;
import static jp.example.filter.AutoTransactionFilter.*;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import jp.example.entity.Item;
import lombok.extern.slf4j.Slf4j;

/**
 * コンテキストルートのトップ画面を表示するインデックス Servlet クラスです。
 * @author Pleiades New Gradle Project Wizard
 */
@WebServlet("")
@Slf4j
public class IndexServlet extends HttpServlet {
	
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse res) {
		
		log.debug("----- [DEBUG PRINT] item テーブル全件 SELECT (クラス指定) -----");
		dao().query(Item.class).stream().forEach(out::println);
		
		log.debug("----- [DEBUG PRINT] users テーブル全件 SELECT (SQL 指定、Map で取得) -----");
		dao().queryWith("SELECT * from users").stream().forEach(out::println);
		
		// 必要に応じて、トップ画面に表示するコンテンツなどを取得
		forward("index.jsp");
	}
}
