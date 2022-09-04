<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://cdn.simplecss.org/simple.min.css">
<link rel="stylesheet" href="${ctx}/static/common.css">
<title>タイトル</title>
</head>
<body>
<header onclick="location.href='${ctx}'">
	<h1>インデックス画面</h1>
	<p>Servlet JSP CRUD サンプル</p>
</header>
<main class="_center">
	<aside><p><shiro:principal/><br><a href="${ctx}/logout">ログアウト</a></p></aside>
	<blockquote id="_message">${fn:escapeXml(MESSAGE)}</blockquote>
	<button onclick="location.href = '${ctx}/item/list'">アイテム一覧</a>
</main>
<footer>
	<p>Generated by Pleiades All in One New Gradle Project Wizard</p>
</footer>
</body>
</html>
