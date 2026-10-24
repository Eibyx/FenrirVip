"""Execute the production VKScript against simulated API methods, not live VK."""
import json
from pathlib import Path
import re
import subprocess

source = (Path(__file__).resolve().parents[1] / 'app_fenrir/src/main/kotlin/dev/ragnarok/fenrir/api/impl/CommentsApi.kt').read_text()
expression = source.split('it.get(', 1)[1].split('\n                    sourceType,', 1)[0]
expression = expression.replace('Constants.API_VERSION', '"test-version"')
code = ''.join(json.loads(token) for token in re.findall(r'"(?:\\.|[^"\\])*"', expression))
script = r'''
const assert = require('node:assert/strict');
const execute = new Function('API', 'Args', CODE);
let checks = 0;
for (const type of ['topic', 'post', 'photo', 'video']) {
  for (const sort of ['asc', 'desc']) {
    const calls = [];
    const args = {source_type:type, owner_id:-42, source_id:123, offset:2,
      count:2, sort, start_comment_id:null, comment_id:0, fields:'id', access_key:'public-test'};
    const method = params => {
      calls.push(params);
      const ids = params.sort === 'desc' ? [90,70,50,30,10] : [10,30,50,70,90];
      return {count:5, items:ids.slice(Number(params.offset || 0), Number(params.offset || 0)+Number(params.count)).map(id=>({id}))};
    };
    const api = {groups:{getById:()=>({groups:[{admin_level:2}]})},
      board:{getComments:method}, wall:{getComments:method},
      photos:{getComments:method}, video:{getComments:method}};
    const result = execute(api,args);
    assert.deepEqual(result.main.items.map(x=>x.id), sort === 'asc' ? [50,70] : [50,30]);
    assert.equal(calls[0].offset,2);
    if(type!=='topic') assert.equal(calls[0].start_comment_id,null);
    assert.equal(calls[0].sort,sort);
    assert.equal(result.admin_level,2);
    if(type==='topic') {
      assert.equal(calls.length,1);
      assert.equal('start_comment_id' in calls[0],false);
      assert.equal(calls[0].group_id,42);
      assert.equal(calls[0].topic_id,123);
      assert.equal(result.first_id,0);
      assert.equal(result.last_id,0);
    } else {
      assert.equal(calls.length,3);
      assert.equal(result.first_id,10);
      assert.equal(result.last_id,90);
      assert.equal(calls[0].owner_id,-42);
      if(type==='photo') assert.equal(calls[0].access_key,'public-test');
    }
    checks++;
  }
}
let focused;
execute({board:{getComments:p=>{focused=p;return {count:2,items:[{id:50}]};}}},
  {source_type:'topic',owner_id:42,source_id:123,offset:-1,count:20,sort:'asc',start_comment_id:50,comment_id:0});
assert.equal(focused.start_comment_id,50);
assert.equal(focused.offset,-1);
checks++;
for (const main of [{count:0,items:[]},false]) {
  let calls=0;
  const result = execute({board:{getComments:()=>{calls++;return main;}}},
    {source_type:'topic',owner_id:42,source_id:123,offset:0,count:20,sort:'asc',comment_id:0});
  assert.deepEqual(result.main,main);
  assert.equal(calls,1); // no unsafe boundary probes on empty/failed pages
  checks++;
}
console.log(`${checks} production VKScript fixture cases passed (mock API, not live VK)`);
'''.replace('CODE', json.dumps(code))
subprocess.run(['node', '-e', script], check=True)
