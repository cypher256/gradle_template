//-----------------------------------------------------------------------------
// JSP、React、Vue 共通
//-----------------------------------------------------------------------------

/**
 * axios 2xx 以外の共通エラー処理をインターセプターに設定します。
 */
axios.interceptors.response.use(
	res => res,
	error => {
		id_message.textContent = (error.response?.status == 401)
			? `❌ セッションが切れました。ページを更新してください。`
			: `❌ 処理できませんでした。 [${error.message}] ${error.config?.url}`;
		return Promise.reject(error);
	}
);

/**
 * HTML form を指定して axios で使用する URLSearchParams を作成します。
 * HTTP get の場合は URL に + で連結、post の場合はそのまま第 2 引数に渡します。
 * @param {object} HTML form 要素
 * @returns {URLSearchParams} 引数の htmlForm のパラメータ
 */
const params = htmlForm => new URLSearchParams(new FormData(htmlForm));
