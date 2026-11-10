import test from 'node:test';
import assert from 'node:assert/strict';
import { createPlan } from './rehearsal/plan.mjs';
test('default rehearsal plans no database, browser or fault actions and labels omitted checks unverified',()=>{
 const p=createPlan();assert.deepEqual(p.backendClasses,[]);assert.deepEqual(p.browserSuites,[]);assert.ok(p.unverified.includes('Spring/MySQL/Redis/API journey'));assert.ok(p.unverified.includes('Dependency outage rehearsal'));
});
test('isolated backend plans use only known Testcontainers classes, with no URL or destructive Compose commands',()=>{
 const p=createPlan({backend:true});assert.equal(p.backendClasses.length,3);assert.ok(p.backendClasses.every(c=>c.startsWith('com.tagme.integration.')));assert.doesNotMatch(JSON.stringify(p),/https?:|compose|down|volume|password|token/i);
 assert.equal(createPlan({backend:true,fullRegression:true}).backendClasses.length,10);
});
test('faults require an explicit backend selection and external connection overrides are rejected by names only',()=>{
 assert.throws(()=>createPlan({faults:true}),/require Backend/);
 for(const key of ['SPRING_DATASOURCE_URL','SPRING_FLYWAY_URL','SPRING_DATA_REDIS_HOST','SPRING_APPLICATION_JSON','SPRING_CONFIG_ADDITIONAL_LOCATION','TAGME_AUTH_SENDER_PASSWORD'])assert.throws(()=>createPlan({backend:true},[key]),/Values were not read/);
 assert.equal(createPlan({backend:true,faults:true},[]).faults,true);
});
