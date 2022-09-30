<!-- 編集コンポーネント -->
<script setup>

	const router = useRouter();
	const id = useRoute().params.id;
	const isInsert = id == 0;
	const form = ref({});
	const companySelect = ref([]);
	const getFormParams = () => {return new URLSearchParams(new FormData(id_form))};
	onMounted(() => {handleInit()});

	// 初期表示 → 取得 API 呼び出し   
	const handleInit = async() => {
		id_message.textContent = null;
		if (!isInsert) {
			const resData = (await axios.get('select?id=' + id)).data;
			if (typeof resData === 'string') {
				id_message.textContent = resData; // エラーメッセージ String
				router.push('/');
				return;
			} else {
				form.value = resData; // ItemForm json
			}
		}
		companySelect.value = (await axios.get('select-company')).data;
  	};
</script>
<template>
	<form id="id_form" method="post" onsubmit="id_submit_button.disabled = true">
		<input type="hidden" name="id" :value="form.id"/>
		<div class="mb-3">
			<label class="form-label">製品名</label> <span class="badge bg-danger">必須</span>
			<input class="form-control" type="text" name="name" :value="form.name"
				onkeyup="validate()" required autofocus onfocus="this.setSelectionRange(99,99)" size="40">
		</div>
		<div class="mb-3">
			<label class="form-label">発売日</label> <span class="badge bg-danger">必須</span>
			<input class="form-control w-auto" type="date" name="releaseDate" :value="form.releaseDate"
				onchange="validate()" required>
		</div>
		<div class="mb-3 form-check">
			<input type="checkbox" name="faceAuth" id="faceAuth" class="form-check-input"
				onchange="validate()" :checked="form.faceAuth">
			<label class="form-check-label" for="faceAuth">顔認証</label>
		</div>
		<div class="mb-5">
			<label class="form-label">メーカー</label>
			<select name="companyId" class="form-select w-auto">
				<option v-for="com in companySelect" :value="com.id" :selected="form.companyId == com.id"
					>{{com.companyName}}</option>
			</select>
		</div>
		<router-link to="/" class="btn btn-secondary px-5 me-1">戻る</router-link>
		<input id="id_submit_button" type="submit" class="btn btn-warning px-5" :value="isInsert ? '登録' : '更新'"/>
	</form>
</template>
