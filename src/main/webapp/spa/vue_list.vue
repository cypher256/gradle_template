<!-- 一覧コンポーネント -->
<script setup>

	const form = window._VueListForm ??= {name:'', releaseDate:''};
	const formList = ref([]);
	const getFormParams = () => new URLSearchParams(new FormData(id_form));
	onMounted(() => handleSearch());

	// 検索 API 呼び出し   
	const handleSearch = async() => {
		const resData = (await axios.get('search?' + getFormParams())).data;
		typeof resData === 'string' ? id_message.textContent = resData : formList.value = resData;
  	};
  	
  	// 検索ボタンクリック、フォーム Enter → 検索 API 呼び出し
	const handleSubmit = async(e) => {
		id_message.textContent = null;
		handleSearch();
  	};

	// 製品名・発売日変更イベント → 件数取得 API 呼び出し   
	const handleChange = async(e) => {
		window._VueListForm[e.target.name] = e.target.value;
		const infoMessage = (await axios.get('count?' + getFormParams())).data;
		id_message.textContent = infoMessage;
  	};
	
	// 削除ボタンクリック → 削除 API 呼び出し (削除は状態変更操作のため post、axios により CSRF ヘッダが自動追加)
	const handleDelete = async(id) => {
		id_message.textContent = (await axios.post('delete?id=' + id)).data || 'ℹ️ 削除しました。';
		handleSearch();
  	};
</script>
<template>
	<form id="id_form" method="get" class="d-sm-flex flex-wrap align-items-end" @submit.prevent="handleSubmit">
		<label class="form-label me-sm-3">製品名</label>
		<div class="me-sm-4">
			<input class="form-control" type="search" name="name" autofocus onfocus="this.setSelectionRange(99,99)"
				@keyup="e => {if (e.keyCode != 13) handleChange(e)}" :value="form.name"><!-- @change が動作しない -->
		</div>
		<label class="form-label me-sm-3">発売日</label>
		<div class="me-sm-4">
			<input class="form-control w-auto mb-3 mb-sm-0" type="date" name="releaseDate"
				@change="handleChange" :value="form.releaseDate">
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
