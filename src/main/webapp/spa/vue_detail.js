const Detail = { 
	data() {
		return {
		}
	},
	template: `
		<router-link to="/" class="btn btn-secondary me-1">戻る</router-link>
		<br>{{$route.params.id}}
	`
};
