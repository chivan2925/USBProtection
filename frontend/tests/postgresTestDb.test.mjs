import assert from 'node:assert/strict'
import test from 'node:test'
import { createPostgresBrowserTestRun, postgresBrowserChildEnv } from './postgresTestDb.mjs'

const env = { USBSHIELD_TEST_DB_PASSWORD: 'test-password-only' }

test('different browser runs get isolated schemas in the disposable database', () => {
  const first = createPostgresBrowserTestRun(env)
  const second = createPostgresBrowserTestRun(env)
  assert.notEqual(first.schema, second.schema)
  for (const config of [first, second]) {
    assert.match(config.schema, /^usbshield_e2e_[0-9a-f]{32}$/)
    assert.equal(config.env.USBSHIELD_DB_USERNAME, 'usbshield_test')
    assert.equal(config.env.USBSHIELD_DB_PASSWORD, 'test-password-only')
    assert.match(config.env.USBSHIELD_DB_URL, new RegExp(`/usbshield_browser_test\\?currentSchema=${config.schema}$`))
    assert.ok(config.datasourceArgs.includes(`--spring.datasource.url=${config.env.USBSHIELD_DB_URL}`))
    assert.ok(config.datasourceArgs.includes(`--spring.datasource.hikari.jdbc-url=${config.env.USBSHIELD_DB_URL}`))
    assert.ok(config.datasourceArgs.includes('--spring.datasource.username=usbshield_test'))
    assert.ok(config.datasourceArgs.includes('--spring.profiles.active=default'))
    assert.ok(config.datasourceArgs.every(arg => !arg.includes('test-password-only')))
    assert.ok(config.hibernateArgs.includes(`--spring.jpa.properties.hibernate.default_schema=${config.schema}`))
    assert.ok(config.hibernateArgs.includes('--spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true'))
  }
})

test('refuses admin or application role and missing password', () => {
  for (const role of ['postgres', 'usbshield', 'other_role']) {
    assert.throws(() => createPostgresBrowserTestRun({ ...env, USBSHIELD_TEST_DB_USERNAME: role }), /require.*usbshield_test/)
  }
  assert.throws(() => createPostgresBrowserTestRun({}), /USBSHIELD_TEST_DB_PASSWORD/)
})

test('rejects malformed host/port instead of constructing a JDBC URL', () => {
  assert.throws(() => createPostgresBrowserTestRun({ ...env, USBSHIELD_TEST_DB_HOST: 'localhost/foo' }), /host\/port/)
  assert.throws(() => createPostgresBrowserTestRun({ ...env, USBSHIELD_TEST_DB_PORT: '65536' }), /host\/port/)
})

// Regression: exported app DB settings must never override the isolated test DB.
test('inherited application datasource variables cannot redirect browser tests into usbshield', () => {
  const parent = {
    USBSHIELD_DB_URL: 'jdbc:postgresql://127.0.0.1:5432/usbshield',
    USBSHIELD_DB_USERNAME: 'usbshield',
    USBSHIELD_DB_PASSWORD: 'app-password',
    SPRING_DATASOURCE_URL: 'jdbc:postgresql://127.0.0.1:5432/usbshield',
    SPRING_DATASOURCE_USERNAME: 'usbshield',
    SPRING_DATASOURCE_PASSWORD: 'app-password',
    SPRING_DATASOURCE_HIKARI_JDBC_URL: 'jdbc:postgresql://127.0.0.1:5432/usbshield',
    SPRING_DATASOURCE_HIKARI_USERNAME: 'usbshield',
    SPRING_DATASOURCE_HIKARI_PASSWORD: 'app-password',
    SPRING_APPLICATION_JSON: '{"spring":{"datasource":{"url":"jdbc:postgresql://127.0.0.1:5432/usbshield"}}}',
    SPRING_CONFIG_ADDITIONAL_LOCATION: 'C:/example/production.yml',
    SPRING_DATASOURCE_JNDI_NAME: 'java:comp/env/production',
    SPRING_PROFILES_ACTIVE: 'prod',
    USBSHIELD_TEST_DB_PASSWORD: 'test-password-only',
  }
  const config = createPostgresBrowserTestRun(parent)
  const child = postgresBrowserChildEnv(parent, config)
  assert.equal(child.USBSHIELD_DB_URL, config.jdbcUrl)
  assert.equal(child.SPRING_DATASOURCE_URL, config.jdbcUrl)
  assert.equal(child.SPRING_DATASOURCE_USERNAME, 'usbshield_test')
  assert.equal(child.SPRING_DATASOURCE_PASSWORD, 'test-password-only')
  assert.equal(child.SPRING_DATASOURCE_HIKARI_JDBC_URL, undefined)
  assert.equal(child.SPRING_DATASOURCE_JNDI_NAME, undefined)
  assert.equal(child.SPRING_DATASOURCE_HIKARI_USERNAME, undefined)
  assert.equal(child.SPRING_DATASOURCE_HIKARI_PASSWORD, undefined)
  assert.equal(child.SPRING_APPLICATION_JSON, undefined)
  assert.equal(child.SPRING_CONFIG_ADDITIONAL_LOCATION, undefined)
  assert.ok(config.datasourceArgs.includes(`--spring.datasource.url=${config.jdbcUrl}`))
  assert.ok(config.datasourceArgs.includes(`--spring.datasource.hikari.jdbc-url=${config.jdbcUrl}`))
  assert.ok(config.datasourceArgs.includes('--spring.profiles.active=default'))
  assert.ok(config.datasourceArgs.every(arg => !arg.includes('app-password')))
})
