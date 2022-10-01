/* React 一覧コンポーネント */
window._List = () => {
   
	const form = window._ReactSearchForm ??= {name:'', releaseDate:''};
	const [formList, setFormList] = useState([]);
	const getFormParams = () => new URLSearchParams(new FormData(id_form));
	useEffect(() => {handleSearch()}, []);

	// 検索 API 呼び出し   
	const handleSearch = async() => {
		const data = (await axios.get('search?' + getFormParams())).data;
		typeof data === 'string' ? id_message.textContent = data : setFormList(data);
  	};
  	
  	// 検索ボタンクリック、フォーム Enter → 検索 API 呼び出し
	const handleSubmit = async(e) => {
		e.preventDefault(); // デフォルトサブミット抑止
		id_message.textContent = null;
		handleSearch();
  	};

	// 製品名・発売日変更イベント → 件数取得 API 呼び出し   
	const handleChange = async(e) => {
		window._ReactSearchForm[e.target.name] = e.target.value;
		const infoMessage = (await axios.get('count?' + getFormParams())).data;
		id_message.textContent = infoMessage;
  	};
	
	// 削除ボタンクリック → 削除 API 呼び出し (削除は状態変更操作のため post、axios により CSRF ヘッダ自動追加)
	const handleDelete = async(id) => {
		id_message.textContent = (await axios.post('delete', 'id=' + id)).data || 'ℹ️ 削除しました。';
		handleSearch();
  	};
  	
	return (
<HashRouter>
	<form id="id_form" method="get" className="d-sm-flex flex-wrap align-items-end" onSubmit={handleSubmit}>
		<label className="form-label me-sm-3">製品名</label>
		<div className="me-sm-4">
			<input className="form-control" type="search" name="name" autoFocus 
				onChange={handleChange} defaultValue={form.name}/>
		</div>
		<label className="form-label me-sm-3">発売日</label>
		<div className="me-sm-4">
			<input className="form-control w-auto mb-3 mb-sm-0" type="date" name="releaseDate" 
				onChange={handleChange} defaultValue={form.releaseDate}/>
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
