-- Retain historical selections; only industry/development remain active.
UPDATE interests SET active=FALSE WHERE category='TECH_STACK';
