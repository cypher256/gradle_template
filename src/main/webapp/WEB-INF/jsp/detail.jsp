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
	<h1>${empty item || item.id == 0 ? '登録' : '変更'}画面</h1>
	<p>Servlet JSP CRUD サンプル</p>
</header>
<main>
<blockquote style="visibility:${empty _message ? 'hidden' : ''}">${fn:escapeXml(_message)}&nbsp;</blockquote>
<form method="post">
	<input type="hidden" name="id" value="${item.id}"/>
	<p><label>製品名 <mark>必須</mark></label><input type="text" name="name" value="${fn:escapeXml(item.name)}"
		autofocus onfocus="this.setSelectionRange(99,99)" size="40"></p>
	<p><label>発売日</label><input type="date" name="releaseDate" value="${fn:escapeXml(item.releaseDate)}"></p>
	<p><label>顔認証</label><input type="checkbox" name="faceAuth" ${item.faceAuth ? 'checked' : ''}></p>
	<button type="button" onclick="location.href='${searchUrl}'">戻る</button>
	<input type="submit" value=
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
</html>
