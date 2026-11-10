// A fixed local-only execution plan. Never accepts an API URL, credentials or a database target.
import { pathToFileURL } from 'node:url';
export function createPlan({ backend=false, browser=false, faults=false, fullRegression=false }={}, environmentKeys=[]) {
  if((faults||fullRegression)&&!backend)throw new Error('Faults/FullRegression require Backend.');
  if(backend){
    const unsafe=environmentKeys.filter(key=>/^SPRING_(DATASOURCE|FLYWAY|REDIS|DATA_REDIS|CONFIG|APPLICATION_JSON)/i.test(key)||/^TAGME_AUTH_/i.test(key));
    if(unsafe.length)throw new Error('Open a separate test shell without external datasource, Redis, config or authentication overrides. Values were not read.');
  }
  const core=['com.tagme.integration.EventRehearsalIntegrationTest','com.tagme.integration.InfrastructureIntegrationTest','com.tagme.integration.PostEventIntegrationTest'];
  const existing=['AuthIntegrationTest','ParticipationIntegrationTest','ResultsIntegrationTest','ContentIntegrationTest','ReactionIntegrationTest','DiscoveryIntegrationTest','OperationsIntegrationTest'].map(c=>'com.tagme.integration.'+c);
  return {
    backend, browser, faults, fullRegression,
    backendClasses:backend?[...core,...(fullRegression?existing:[])]:[],
    browserSuites:browser?['test:auth-nfc-ui','test:participation-ui','test:results-ui','test:content-ui','test:reactions-ui','test:discovery-ui','test:operations-ui']:[],
    browserScripts:browser?['scripts/check-polish-ui.mjs','scripts/check-awards-ui.mjs','scripts/check-post-event-ui.mjs']:[],
    unverified:[...(!backend?['Spring/MySQL/Redis/API journey']:[]),...(!browser?['Browser rendering/contracts']:[]),...(!faults?['Dependency outage rehearsal']:[]),'Physical NFC/iOS/Android/application restart'],
  };
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  try{
    const flags=process.argv.slice(2),allowed=['--backend','--browser','--faults','--full-regression'];
    if(flags.some(f=>!allowed.includes(f)))throw new Error('Only documented local rehearsal switches are accepted.');
    if(Number(process.versions.node.split('.')[0])!==24)throw new Error('Use the project-required Node 24.');
    console.log(JSON.stringify(createPlan({backend:flags.includes('--backend'),browser:flags.includes('--browser'),faults:flags.includes('--faults'),fullRegression:flags.includes('--full-regression')},Object.keys(process.env))));
  }catch(error){console.error(error.message);process.exitCode=1;}
}
