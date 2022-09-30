/* 
React Router 定義 (HashRouter 使用)
・BrowserRouter : URL が切り替わるため、サーバ側で URL マッピングが必要 (同じページを返すようにする)
・HashRouter    : URL ハッシュで切り替えるため、サーバ側で URL マッピング不要 (だが引数で state を渡せない)
*/
const App = () => {
	return (
		<HashRouter>
			<Route path="/" exact component={window._List} />
			<Route path="/edit/:id" component={window._Edit} />
		</HashRouter>
	);
};
ReactDOM.createRoot(id_root).render(<App />); // React 18 以降は createRoot 推奨
// TODO html に移動

/* 一覧コンポーネント */
window._List = () => {
   
	const form = window._ListForm ??= {name:'', releaseDate:''};
	const [formList, setFormList] = useState([]);
	const getFormParams = () => new URLSearchParams(new FormData(id_form));
	useEffect(() => {handleSearch()}, []);

	// 検索ボタンクリック → 検索 API 呼び出し   
	const handleSearch = async() => {
		const res = (await axios.get('search?' + getFormParams())).data;
		typeof res === 'string' ? id_message.textContent = res : setFormList(res);
		//TODO json 判定 axios 機能？
  	};
  	
  	// フォーム Enter → 検索 API 呼び出し
	const handleSubmit = async(e) => {
		e.preventDefault(); // デフォルトサブミット抑止
		id_message.textContent = null;
		handleSearch();
  	};

	// 製品名・発売日変更イベント → 件数取得 API 呼び出し   
	const handleChange = async(target) => {
		//form[target.name] = target.value;
		window._ListForm[target.name] = target.value;
		const infoMessage = (await axios.get('count?' + getFormParams())).data;
		id_message.textContent = infoMessage;
  	};
	
	// 削除ボタンクリック → 削除 API 呼び出し (削除は状態変更操作のため post、axios により CSRF ヘッダが自動追加)
	const handleDelete = async(id) => {
		id_message.textContent = (await axios.post('delete?id=' + id)).data || 'ℹ️ 削除しました。';
		handleSearch();
  	};
  	
	return (
<HashRouter>
	<form id="id_form" method="get" className="d-sm-flex flex-wrap align-items-end" onSubmit={handleSubmit}>
		<label className="form-label me-sm-3">製品名</label>
		<div className="me-sm-4">
			<input className="form-control" type="search" name="name" autoFocus 
				onChange={e => handleChange(e.target)} defaultValue={form.name}/>
		</div>
		<label className="form-label me-sm-3">発売日</label>
		<div className="me-sm-4">
			<input className="form-control w-auto mb-3 mb-sm-0" type="date" name="releaseDate" 
				onChange={e => handleChange(e.target)} defaultValue={form.releaseDate}/>
		</div>
		<button type="submit" className="btn btn-secondary px-5">検索</button>
		<Link to="/edit/0" className="btn btn-secondary px-5 ms-auto">新規登録</Link>
	</form>
	<p className="text-end mt-4 me-1 mb-2">検索結果 {formList.length} 件</p>
	<table className="table table-striped table-dark">
		<thead>
			<tr className={formList.length == 0 ? 'd-none' : ''}>
				<th>製品名</th>
				<th>発売日</th>
				<th className="text-center">顔認証</th>
				<th>メーカー</th>
				<th className="text-center">操作</th>
			</tr>
		</thead>
		<tbody>
	{formList.map(form => (
			<tr key={form.id}>
				<td>{form.name}</td>
				<td>{form.releaseDate}</td>
				<td className="text-center">{form.faceAuth ? '○' : ''}</td>
				<td>{form.companyName}</td>
				<td className="text-center">
					<Link to={'/edit/' + form.id} className="btn btn-secondary me-1">変更</Link>
					<button type="button" onClick={() => handleDelete(form.id)} className="btn btn-warning">削除</button>
				</td>
			</tr>
	))}
		</tbody>
	</table>
</HashRouter>
	);
}
