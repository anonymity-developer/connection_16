import test from 'node:test';
import assert from 'node:assert/strict';
import { cancelTapAttempt } from '../src/features/results/cancelRecovery.ts';
const attempt=(id,status)=>({attemptId:id,generation:id,status,serverNow:'2030-01-01T00:00:00Z',startsAt:'2030-01-01T00:00:05Z',endsAt:'2030-01-01T00:00:15Z',submitDeadline:'2030-01-01T00:00:20Z',tapCount:null,reviewReason:null,provisional:false});
test('late CANCELLED reply cannot offer a new official chance after another tab starts a newer attempt',async()=>{
 const server={officialAvailable:false,attempt:attempt(2,'PLAYING')};let reads=0;
 const next=await cancelTapAttempt(1,{cancel:async()=>attempt(1,'CANCELLED'),tap:async()=>{reads++;return server;}});
 assert.deepEqual(next,server);assert.equal(reads,1);
});
test('unknown cancellation reconciliation never manufactures an available official slot',async()=>{
 await assert.rejects(cancelTapAttempt(1,{cancel:async()=>attempt(1,'CANCELLED'),tap:async()=>{throw new Error('fixture read unavailable');}}),/fixture read unavailable/);
});

test('a consumed official cancellation reply stays unavailable without a new slot read',async()=>{
 for(const status of ['INTERRUPTED','VALID','REVIEW_REQUIRED','EXPIRED']){
  const saved=attempt(1,status);const result=await cancelTapAttempt(1,{cancel:async()=>saved,tap:async()=>assert.fail('no new chance should be inferred')});
  assert.deepEqual(result,{officialAvailable:false,attempt:saved});
 }
});
