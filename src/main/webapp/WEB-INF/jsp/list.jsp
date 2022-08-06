<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://cdn.simplecss.org/simple.min.css">
<link rel="stylesheet" href="common.css">
<title>タイトル</title>
</head>
<body>
<header onclick="location.href='.'">
	<h1>一覧画面</h1>
	<p>Servlet JSP CRUD サンプル</p>
</header>
<main>
<blockquote style="visibility:${empty _message ? 'hidden' : ''}">${fn:escapeXml(_message)}&nbsp;</blockquote>
<form method="get">
	<p><label>製品名</label><input type="text" name="name" value="${fn:escapeXml(param.name)}"
		autofocus onfocus="this.setSelectionRange(99,99)"></p>
	<p><label>発売日</label><input type="date" name="releaseDate" value="${fn:escapeXml(param.releaseDate)}"></p>
	<button formaction=".">検索</button>
	<button formaction="create">新規登録</button>
</form>
<table>
	<tr style="display:${empty itemList ? 'none' : ''}">
		<th>製品名</th>
		<th>発売日</th>
		<th class="_center">顔認証</th>
		<th class="_center">操作</th>
	</tr>
	<tbody>
<c:forEach var="item" items="${itemList}">
		<tr>
			<td>${fn:escapeXml(item.name)}</td>
			<td>${fn:escapeXml(item.releaseDate)}</td>
			<td class="_center">${item.faceAuth ? '○' : ''}</td>
			<td class="_center">
				<button type="button" onclick="location.href='update?id=${item.id}'">変更</button>
				<button type="button" onclick="location.href='delete?id=${item.id}'">削除</button>
			</td>
		</tr>
</c:forEach>
	</tbody>
</table>
</main>
<footer>
	<p>Generated by Pleiades All in One New Gradle Project Wizard</p>
</footer>
</body>
</html>
