<?php
return [
  'db_host' => getenv('HOSPITAL_DB_HOST') ?: '127.0.0.1',
  'db_name' => getenv('HOSPITAL_DB_NAME') ?: 'smart_hospital',
  'db_user' => getenv('HOSPITAL_DB_USER') ?: 'root',
  'db_pass' => getenv('HOSPITAL_DB_PASSWORD') ?: '',
  'service_token' => getenv('HOSPITAL_PHP_TOKEN') ?: 'smart-hospital-local-token'
];
