//-----------------------------------------------------------------------------
// JSP、React、Vue 共通
//-----------------------------------------------------------------------------

// axios 2xx 以外の共通エラー処理
axios.interceptors.response.use(
	res => res,
	error => {
		id_message.textContent = (error.response?.status == 401)
			? `❌ セッションが切れました。ページを更新してください。`
			: `❌ 処理できませんでした。 ${error.message} - ${error.config?.url}`;
		return Promise.reject(error);
	}
);
