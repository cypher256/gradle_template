<!-- 一覧コンポーネント -->
<script setup>

	const formList = ref([]);
	const getFormParams = () => {return new URLSearchParams(new FormData(id_form))};
	onMounted(() => handleSearch());

	// 検索 API 呼び出し   
	const handleSearch = async() => {
		const resData = (await axios.get('search?' + getFormParams())).data;
		typeof resData === 'string' ? id_message.textContent = resData : formList.value = resData;
  	};
</script>
<template>
	<form id="id_form" method="get" class="d-sm-flex flex-wrap align-items-end">
		<label class="form-label me-sm-3">製品名</label>
		<div class="me-sm-4">
			<input class="form-control" type="search" name="name" value=""
				onkeyup="" autofocus onfocus="this.setSelectionRange(99,99)">
		</div>
		<label class="form-label me-sm-3">発売日</label>
		<div class="me-sm-4">
			<input class="form-control w-auto mb-3 mb-sm-0" type="date" name="releaseDate"
				value="" onchange="">
		</div>
		<button type="submit" class="btn btn-secondary px-5">検索</button>
		<button class="btn btn-secondary px-5 ms-auto">新規登録</button>
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
					<button type="button" class="btn btn-warning">削除</button>
				</td>
			</tr>
		</tbody>
	</table>
</template>
