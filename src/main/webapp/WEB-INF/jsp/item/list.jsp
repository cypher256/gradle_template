<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://unpkg.com/simpledotcss/simple.min.css">
<link rel="stylesheet" href="${ctx}/static/common.css">
<title>タイトル</title>
</head>
<body>
<header onclick="location.href='${ctx}'">
	<h1>一覧画面</h1>
	<p>Servlet JSP CRUD サンプル</p>
</header>
<main>
	<aside><p>${request.remoteUser}<br><a href="${ctx}/logout">ログアウト</a></p></aside>
	<blockquote id="_message">${fn:escapeXml(MESSAGE)}</blockquote>
	<form id="_form" method="get">
		<p>
			<label>製品名</label>
			<input type="search" name="name" value="${fn:escapeXml(param.name)}"
				onkeyup="count()" autofocus onfocus="this.setSelectionRange(99,99)">
		</p>
		<p>
			<label>発売日</label>
			<input type="date" name="releaseDate" value="${fn:escapeXml(param.releaseDate)}"
				onchange="count()">
		</p>
		<button formaction="list">検索</button>
		<button formaction="create">新規登録</button>
	</form>
	<p style="margin:0 0.3rem -1rem; text-align:right;">検索結果 ${itemList.size()} 件</p>
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
<script src="https://unpkg.com/axios/dist/axios.min.js"></script>
<script>
<%-- 検索条件入力中のリアルタイム API 件数 JSON 取得 (form が get のため _csrf が含まれない) --%>
const count = async() => {
	const res = (await axios.get('api?' + new URLSearchParams(new FormData(_form)))).data;
	_message.textContent = res.count != null ? '結果予想件数: ' + res.count + ' 件' : res;
};
</script>
</html>
