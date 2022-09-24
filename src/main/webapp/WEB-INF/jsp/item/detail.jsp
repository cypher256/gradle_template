<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap@5/dist/css/bootstrap.min.css">
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5/dist/js/bootstrap.bundle.min.js"></script>
<title>タイトル</title>
</head>
<body class="bg-dark bg-gradient text-light vh-100">
<nav class="navbar navbar-expand-lg navbar-dark bg-dark mb-1">
	<div class="container">
		<a class="navbar-brand" href="${ctx}">Servlet CRUD サンプル</a>
		<button class="navbar-toggler" type="button"
			data-bs-toggle="collapse" data-bs-target="#navbarSupportedContent">
			<span class="navbar-toggler-icon"></span>
		</button>
		<div class="collapse navbar-collapse" id="navbarSupportedContent">
			<div class="navbar-text d-flex ms-auto ${empty request.remoteUser ? 'd-none' : ''}">
				<span class="me-3">${fn:escapeXml(request.remoteUser)}</span>
				<a class="nav-link active" href="${ctx}/logout">ログアウト</a>
			</div>
		</div>
	</div>
</nav>
<div class="container">
 	<div class="alert mb-0" id="_message" style="min-height:4rem">${fn:escapeXml(MESSAGE)}</div>
	<form id="_form" method="post" onsubmit="_submitButton.disabled = true"><%-- 二度押し防止 --%>
		<input type="hidden" name="id" value="${form.id}"/>
		<div class="mb-3">
			<label class="form-label">製品名</label> <span class="badge bg-danger">必須</span>
			<input class="form-control" type="text" name="name" value="${fn:escapeXml(form.name)}"
				onkeyup="validate()" required autofocus onfocus="this.setSelectionRange(99,99)" size="40">
		</div>
		<div class="mb-3">
			<label class="form-label">発売日</label> <span class="badge bg-danger">必須</span>
			<input class="form-control w-auto" type="date" name="releaseDate" value="${fn:escapeXml(form.releaseDate)}"
				onchange="validate()" required>
		</div>
		<div class="mb-3 form-check">
			<input type="checkbox" name="faceAuth" id="faceAuth" class="form-check-input"
				${form.faceAuth ? 'checked' : ''} onchange="validate()">
			<label class="form-check-label" for="faceAuth">顔認証</label>
		</div>
		<div class="mb-5">
			<label class="form-label">メーカー</label>
			<select name="companyId" class="form-select w-auto">
	<c:forEach var="com" items="${form.companySelectOptions}">
				<option value="${com.id}" ${form.companyId == com.id ? 'selected' : ''}
					>${fn:escapeXml(com.companyName)}</option>
	</c:forEach>
			</select>
		</div>
		<a href="${lastQueryUrl == null ? 'list' : lastQueryUrl}" class="btn btn-secondary px-5">戻る</a>
		<input id="_submitButton" type="submit" class="btn btn-warning px-5" value=
			${form.id == 0
				? '"登録" formaction="create"' 
				: '"更新" formaction="update"'
			}/>
</div>
<footer class="footer fixed-bottom py-3 text-center bg-dark">
	<div class="container">
		<span class="text-muted">New Gradle Project Wizard (c) Pleiades MIT</span>
	</div>
</footer>
</body>
<script src="https://unpkg.com/axios/dist/axios.min.js"></script>
<script>
<%-- axios で post (_csrf 有り、入力中のリアルタイム API チェック結果を文字列で取得) --%>
const validate = async() => {
	_message.textContent = (await axios.post('api', new URLSearchParams(new FormData(_form)))).data;
};
</script>
</html>
