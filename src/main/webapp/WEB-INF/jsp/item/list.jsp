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
			<ul class="navbar-nav me-auto mb-2 mb-lg-0">
				<li class="nav-item"><a class="nav-link active" href="${ctx}/item/list">JSP</a></li>
				<li class="nav-item"><a class="nav-link" href="#">React</a></li>
				<li class="nav-item"><a class="nav-link" href="#">Vue</a></li>
			</ul>
			<div class="navbar-text d-flex ${empty request.remoteUser ? 'd-none' : ''}">
				<span class="me-3">${fn:escapeXml(request.remoteUser)}</span>
				<a class="nav-link active" href="${ctx}/logout">ログアウト</a>
			</div>
		</div>
	</div>
</nav>
<div class="container">
 	<div class="alert mb-0" id="_message" style="min-height:4rem">${fn:escapeXml(MESSAGE)}</div>
	<form id="_form" method="get" class="d-sm-flex flex-wrap align-items-end">
		<label class="form-label me-sm-3">製品名</label>
		<div class="me-sm-5">
			<input class="form-control" type="search" name="name" value="${fn:escapeXml(param.name)}"
				onkeyup="count()" autofocus onfocus="this.setSelectionRange(99,99)">
		</div>
		<label class="form-label me-sm-3">発売日</label>
		<div class="me-sm-5">
			<input class="form-control w-auto mb-3 mb-sm-0" type="date" name="releaseDate"
				value="${fn:escapeXml(param.releaseDate)}" onchange="count()">
		</div>
		<button formaction="list" class="btn btn-secondary px-5 me-1">検索</button>
		<button formaction="create" class="btn btn-secondary px-5">新規登録</button>
	</form>
	<p class="text-end">検索結果 ${formList.size()} 件</p>
	<table class="table table-striped table-dark">
		<thead>
			<tr class="${empty formList ? 'd-none' : ''}">
				<th>製品名</th>
				<th>発売日</th>
				<th class="text-center">顔認証</th>
				<th>メーカー</th>
				<th class="text-center">操作</th>
			</tr>
		</thead>
		<tbody>
	<c:forEach var="form" items="${formList}">
			<tr>
				<td>${fn:escapeXml(form.name)}</td>
				<td>${fn:escapeXml(form.releaseDate)}</td>
				<td class="text-center">${form.faceAuth ? '○' : ''}</td>
				<td>${fn:escapeXml(form.companyName)}</td>
				<td class="text-center">
					<a href="update?id=${form.id}" class="btn btn-secondary">変更</a>
					<%--削除は状態変更操作のため post (_csrf 有り) --%>
					<form method="post" action="delete?id=${form.id}" class="d-inline">
						<button class="btn btn-warning">削除</button>
					</form>
				</td>
			</tr>
	</c:forEach>
		</tbody>
	</table>
</div>
<footer class="footer fixed-bottom py-3 text-center bg-dark">
	<div class="container">
		<span class="text-muted">New Gradle Project Wizard (c) Pleiades MIT</span>
	</div>
</footer>
</body>
<script src="https://unpkg.com/axios/dist/axios.min.js"></script>
<script>
<%-- axios で get (_csrf 無し、検索条件入力中のリアルタイム API 件数 JSON 取得) --%>
const count = async() => {
	const res = (await axios.get('api?' + new URLSearchParams(new FormData(_form)))).data;
	_message.textContent = res.count != null ? '結果予想件数: ' + res.count + ' 件' : res;
};
</script>
</html>
