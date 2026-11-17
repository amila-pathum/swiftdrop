let csrf
export function resetCsrf(){csrf=undefined}
export async function api(path,{method='GET',body}={}){
  const send=async()=>{
    if(method!=='GET'&&!csrf){const response=await fetch('/api/auth/csrf',{credentials:'same-origin',cache:'no-store'});if(!response.ok)throw new Error('Unable to obtain a session token.');csrf=await response.json()}
    return fetch(path,{method,credentials:'same-origin',cache:'no-store',headers:{...(body===undefined?{}:{'Content-Type':'application/json'}),...(method==='GET'?{}:{[csrf.headerName]:csrf.token})},body:body===undefined?undefined:JSON.stringify(body)})
  }
  let response=await send()
  if(response.status===403&&method!=='GET'){resetCsrf();response=await send()}
  if(response.status===204)return null
  const data=await response.json().catch(()=>({error:'The service could not complete this action.'}))
  if(!response.ok){const error=new Error(data.error||'Unable to complete this action.');error.status=response.status;throw error}
  return data
}
