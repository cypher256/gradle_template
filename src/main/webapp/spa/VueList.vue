<!-- Vue 一覧コンポーネント (script setup は Vue 3.2 以降) -->
<script setup>

	const form = window._VueSearchForm ??= {name:'', releaseDate:''}; //一覧に戻ってきたときの条件保存 (null 合体代入)
	const formList = ref([]); // ref で template で使用する値を定義 (コードでは .value でアクセス)
	onMounted(() => handleInit()); // コンポーネントのマウント時の処理 

	// 初期表示 → 検索 API 呼び出し
	const handleInit = async() => {
		document.title = 'Vue の場合 (一覧コンポーネント)';
		id_head_link_server.href = '../item/list';
		id_head_link_react.href = '../spa/react.html';
		id_head_link_vue.href   = '../spa/vue.html';
		handleSearch();
  	};

	// 検索 API 呼び出し   
	const handleSearch = async() => {
		const data = (await axios.get('search?' + params(id_form))).data;
		typeof data === 'string' ? id_message.textContent = data : formList.value = data;
  	};
  	
  	// 検索ボタンクリック、フォーム Enter → 検索 API 呼び出し
	const handleSubmit = async(e) => {
		
		id_message.textContent = null;
		handleSearch();
  	};

	// 検索条件変更イベント → 件数取得 API 呼び出し   
	const handleChange = async(e) => {
		window._VueSearchForm[e.target.name] = e.target.value;
		const countMessage = (await axios.get('count?' + params(id_form))).data;
		id_message.textContent = countMessage
  	};
	
	// 削除ボタンクリック → 削除 API 呼び出し (削除は状態変更操作のため post、axios により CSRF ヘッダ自動追加)
	const handleDelete = async(id) => {
		id_message.textContent = (await axios.post('delete', 'id=' + id)).data || 'ℹ️ 削除しました。';
		handleSearch();
  	};
  	
</script>
<template>
	<form @submit.prevent="handleSubmit" id="id_form" method="get" class="d-sm-flex flex-wrap align-items-end">
		<label class="form-label me-sm-3">製品名</label>
		<div class="me-sm-4">
			<input class="form-control" type="search" name="name" :value="form.name" autofocus
				@input="handleChange">
		</div>
		<label class="form-label me-sm-3">発売日</label>
		<div class="me-sm-4">
			<input class="form-control w-auto mb-3 mb-sm-0" type="date" name="releaseDate" :value="form.releaseDate"
				@change="handleChange">
		</div>
		<button type="submit" class="btn btn-secondary px-5">検索</button>
		<router-link to="/edit/0" class="btn btn-secondary px-5 ms-auto">新規登録</router-link>
	</form>
	<p class="text-end mt-4 me-1 mb-2">検索結果 {{formList.length}} 件</p>
	<table class="table table-striped table-dark">
		<thead>
			<tr class="{{formList.length == 0 ? 'd-none' : ''}}">
				<th>製品名</th>
				<th>発売日</th>
				<th class="text-center">顔認証</th>
				<th>メーカー</th>
				<th class="text-center">操作</th>
			</tr>
		</thead>
		<tbody>
			<tr v-for="form in formList">
				<td>{{form.name}}</td>
				<td>{{form.releaseDate}}</td>
				<td class="text-center">{{form.faceAuth ? '○' : ''}}</td>
				<td>{{form.companyName}}</td>
				<td class="text-center">
					<router-link :to='"/edit/" + form.id' class="btn btn-secondary me-1">変更</router-link>
					<button type="button" @click="() => handleDelete(form.id)" class="btn btn-warning">削除</button>
				</td>
			</tr>
		</tbody>
	</table>
</template>
