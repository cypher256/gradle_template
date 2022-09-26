const { useState, useEffect } = React;
const { HashRouter, Route, Link } = ReactRouterDOM; // v5 (v6 は script タグ未対応)

const AppState = {
	message: null,
	searchForm: {name:'', releaseDate:''},
};

const App = () => {
	return (
		<HashRouter>
			<Route path="/" exact component={List} />
			<Route path="/detail/:id" component={Detail} />
		</HashRouter>
	);
};

const List = () => {
   
	const form = AppState.searchForm;
	const [formList, setFormList] = useState([]);
	const [message, setMessage] = useState();
	useEffect(() => {handleSearch()}, []);

	/* 検索ボタンクリック → 検索 API 呼び出し */   
	const handleSearch = async() => {
		const res = (await axios.get('search?' + new URLSearchParams(new FormData(_form)))).data;
		typeof res === 'string' ? AppState.message = res : setFormList(res);
		setMessage(AppState.message);
		AppState.message = null;
  	};
  	
  	/* フォーム Enter → 検索 API 呼び出し */
	const handleSubmit = async(e) => {
		e.preventDefault(); // デフォルトサブミット抑止
		handleSearch();
  	};

	/* 製品名・発売日変更イベント → 件数取得 API 呼び出し */   
	const handleChange = async(target) => {
		form[target.name] = target.value;
		const res = (await axios.get('count?' + new URLSearchParams(new FormData(_form)))).data;
		setMessage(res);
  	};
	
	/* 削除ボタンクリック → 削除 API 呼び出し (削除は状態変更操作のため post、axios により CSRF ヘッダが自動追加) */
	const handleDelete = async(id) => {
		AppState.message = (await axios.post('delete?id=' + id)).data || 'ℹ️ 削除しました。';
		handleSearch();
  	};
  	
	return (
<HashRouter>
 	<div className="alert mb-0" style={{minHeight:'4rem'}}>{message}</div>
	<form id="_form" method="get" className="d-sm-flex flex-wrap align-items-end" onSubmit={handleSubmit}>
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
		<Link to="/detail/0" className="btn btn-secondary px-5 ms-auto">新規登録</Link>
	</form>
	<p className="text-end mt-4 me-1 mb-2">検索結果 {formList.length} 件</p>
	<table className="table table-striped table-dark">
		<thead>
			<tr className={formList.length == 0 && 'd-none'}>
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
				<td className="text-center">{form.faceAuth && '○'}</td>
				<td>{form.companyName}</td>
				<td className="text-center">
					<Link to={'/detail/' + form.id} className="btn btn-secondary me-1">変更</Link>
					<button type="button" onClick={() => handleDelete(form.id)} className="btn btn-warning">削除</button>
				</td>
			</tr>
			))}
		</tbody>
	</table>
</HashRouter>
	);
}

ReactDOM.createRoot(_root).render(<App />); // v18 以降の推奨の書き方
