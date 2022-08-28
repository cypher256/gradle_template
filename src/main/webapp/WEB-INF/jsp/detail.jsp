<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://cdn.simplecss.org/simple.min.css">
<link rel="stylesheet" href="public/common.css">
<title>タイトル</title>
</head>
<body>
<header onclick="location.href='.'">
	<h1>${empty item || item.id == 0 ? '登録' : '変更'}画面</h1>
	<p>Servlet JSP CRUD サンプル</p>
</header>
<main>
<aside><p><shiro:principal/><br><a href="logout">ログアウト</a></p></aside>
<blockquote id="_message">${fn:escapeXml(MESSAGE)}</blockquote>
<form id="_form" method="post" onsubmit="_submitButton.disabled = true"><%-- 二度押し防止 --%>
	<input type="hidden" name="id" value="${item.id}"/>
	<p><label>製品名 <mark>必須</mark></label>
		<input type="text" name="name" value="${fn:escapeXml(item.name)}"
			onkeyup="validate()" required autofocus onfocus="this.setSelectionRange(99,99)" size="40"></p>
	<p><label>発売日</label>
		<input type="date" name="releaseDate" value="${fn:escapeXml(item.releaseDate)}"
			onchange="validate()"></p>
	<p><label>顔認証</label>
		<input type="checkbox" name="faceAuth" ${item.faceAuth ? 'checked' : ''}
			onchange="validate()"></p>
	<button type="button" onclick="location.href='${searchUrl == null ? '.' : searchUrl}'">戻る</button>
	<input id="_submitButton" type="submit" value=
		${empty item || item.id == 0
			? '"登録" formaction="create"' 
			: '"更新" formaction="update"'
		}/>
</form>
</main>
<footer>
	<p>Generated by Pleiades All in One New Gradle Project Wizard</p>
</footer>
</body>
<script src="https://unpkg.com/axios/dist/axios.min.js"></script>
<script>
<%-- 入力中のリアルタイム API チェック結果を文字列で取得 (form が post のため _csrf が含まれる) --%>
const validate = async() => {
	_message.textContent = (await axios.post('api', new URLSearchParams(new FormData(_form)))).data;
};
</script>
</html>
