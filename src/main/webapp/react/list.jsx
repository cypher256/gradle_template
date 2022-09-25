const { useState, useEffect } = React;

const App = () => {
   
	const [formList, setFormList] = useState([]);
	const [message, setMessage] = useState();
	useEffect(() => {handleSearch()}, []);

	/* 検索ボタンクリック → 検索 API 呼び出し */   
	const handleSearch = async() => {
		const res = (await axios.get('list?' + new URLSearchParams(new FormData(_form)))).data;
		if (Array.isArray(res)) {
			setFormList(res);
			setMessage("");
		} else {
			setMessage(res);
		}
  	};
  	
  	/* フォーム Enter → 検索 API 呼び出し */
	const handleSubmit = async(e) => {
		e.preventDefault(); // デフォルトサブミット抑止
		handleSearch();
  	};

	/* 製品名・発売日変更イベント → 件数取得 API 呼び出し */   
	const handleChange = async() => {
		const res = (await axios.get('count?' + new URLSearchParams(new FormData(_form)))).data;
		setMessage(res);
  	};
	
	/* 削除ボタンクリック → 削除 API 呼び出し (削除は状態変更操作のため post、axios により CSRF ヘッダが自動追加) */
	const handleDelete = async(id) => {
		const res = (await axios.post('delete?id=' + id)).data;
		setMessage(res);
		handleSearch();
  	};
  	
	return (
<div>
 	<div className="alert mb-0" id="_message" style={{minHeight:'4rem'}}>{message}</div>
	<form id="_form" method="get" className="d-sm-flex flex-wrap align-items-end" onSubmit={handleSubmit}>
		<label className="form-label me-sm-3">製品名</label>
		<div className="me-sm-4">
			<input className="form-control" type="search" name="name" autoFocus onChange={handleChange}/>
		</div>
		<label className="form-label me-sm-3">発売日</label>
		<div className="me-sm-4">
			<input className="form-control w-auto mb-3 mb-sm-0" type="date" name="releaseDate" onChange={handleChange}/>
		</div>
		<button type="submit" formAction="list" className="btn btn-secondary px-5">検索</button>
		<button type="button" formAction="create" className="btn btn-secondary px-5 ms-auto">新規登録</button>
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
					<a href="update?id={form.id}" className="btn btn-secondary me-1">変更</a>
					<button type="button" onClick={() => handleDelete(form.id)} className="btn btn-warning">削除</button>
				</td>
			</tr>
			))}
		</tbody>
	</table>
</div>
	);
}

ReactDOM.createRoot(_root).render(<App />);
