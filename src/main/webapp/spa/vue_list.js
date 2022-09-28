const Container = {
	data() {
		return {
			message: 'メッセージ',
			formList: [
				{ 
					name: 'なまえ',
					releaseDate: '2022-12-31',
					faceAuth: true,
					companyName: 'Apple',
				},
				{ 
					name: 'なまえ２',
					releaseDate: '2022-02-03',
					faceAuth: false,
					companyName: 'Sony',
				},
			]
		}
	}
};
Vue.createApp(Container).mount('#_container');
