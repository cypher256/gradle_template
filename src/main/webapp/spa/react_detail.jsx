const { useState, useEffect } = React;
const { HashRouter, Route, Link, useParams, useHistory } = ReactRouterDOM; // v5 (v6 は script タグ未対応)

const Detail = () => {
	
	const [form, setForm] = useState({});
	const [companyId, setCompanyId] = useState();
	const [companySelect, setCompanySelect] = useState([]);
	const [message, setMessage] = useState();
	const id = useParams().id;
	const history = useHistory();
	useEffect(() => {handleInit()}, []);

	/* 初期表示 → 取得 API 呼び出し */   
	const handleInit = async() => {
		if (id != 0) {
			const res = (await axios.get('detail?id=' + id)).data;
			if (typeof res === 'string') {
				AppState.message = res;
				history.goBack();
				return;
			} else {
				setForm(res);
				setCompanyId(res.companyId);
			}
		}
		setCompanySelect((await axios.get('companySelect')).data);
  	};
  	
  	/* フォーム Enter → 登録・更新 API 呼び出し */
	const handleSubmit = async(e) => {
		e.preventDefault(); // デフォルトサブミット抑止
		_submitButton.disabled = true;
		const id0 = id == 0;
		const error = (await axios.post(id0 ? 'insert' : 'update', new URLSearchParams(new FormData(_form)))).data;
		if (error) {
			setMessage(error);
			_submitButton.disabled = false;
			return;
		}
		AppState.message = id0 ? 'ℹ️ 登録しました。' : 'ℹ️ 更新しました。';
		history.goBack();
  	};

	/* 変更イベント → 入力チェック API 呼び出し */   
	const handleChange = async() => {
		const error = (await axios.post('validate', new URLSearchParams(new FormData(_form)))).data;
		setMessage(error);
  	};

	return (
<HashRouter>
 	<div className="alert mb-0" style={{minHeight:'4rem'}}>{message}</div>
	<form id="_form" method="post" onSubmit={handleSubmit}>
		<input type="hidden" name="id" defaultValue={form.id}/>
		<div className="mb-3">
			<label className="form-label">製品名</label> <span className="badge bg-danger">必須</span>
			<input className="form-control" type="text" name="name" id="_name" defaultValue={form.name} size="40"
				onChange={handleChange} required autoFocus/>
		</div>
		<div className="mb-3">
			<label className="form-label">発売日</label> <span className="badge bg-danger">必須</span>
			<input className="form-control w-auto" type="date" name="releaseDate" defaultValue={form.releaseDate}
				onChange={handleChange} required/>
		</div>
		<div className="mb-3 form-check">
			<input type="checkbox" name="faceAuth" id="faceAuth" className="form-check-input"
				onChange={handleChange} defaultChecked={form.faceAuth}/>
			<label className="form-check-label" htmlFor="faceAuth">顔認証</label>
		</div>
		<div className="mb-5">
			<label className="form-label">メーカー</label>
			<select name="companyId" className="form-select w-auto" value={companyId}
				onChange={e => setCompanyId(e.target.value)}>
			{companySelect.map(com => (
				<option key={com.id} value={com.id}>{com.companyName}</option>
			))}
			</select>
		</div>
		<Link to="/" className="btn btn-secondary px-5 me-1">戻る</Link>
		<input id="_submitButton" type="submit" className="btn btn-warning px-5" value=
			{id == 0 ? '登録' : '更新'}/>
	</form>
</HashRouter>
	);
}
