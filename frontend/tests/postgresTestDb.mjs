// PostgreSQL browser tests must not share a mutable schema with any other run.
// Each run creates its own schema inside the dedicated disposable TEST database;
// the Spring application remains pointed away from the application database.
import { randomUUID } from 'node:crypto'
import { spawn } from 'node:child_process'

export function createPostgresBrowserTestRun(env = process.env) {
  const host = env.USBSHIELD_TEST_DB_HOST || '127.0.0.1'
  const port = env.USBSHIELD_TEST_DB_PORT || '5432'
  const username = env.USBSHIELD_TEST_DB_USERNAME || 'usbshield_test'
  const password = env.USBSHIELD_TEST_DB_PASSWORD
  if (!/^[a-zA-Z0-9_.-]+$/.test(host) || !/^\d{1,5}$/.test(port) || Number(port) < 1 || Number(port) > 65535) {
    throw new Error('Invalid PostgreSQL test host/port')
  }
  // The test setup creates the database under this non-admin role.  Enforce it
  // so tests cannot accidentally run destructive DDL with an application/admin role.
  if (username !== 'usbshield_test') {
    throw new Error('Browser live tests require USBSHIELD_TEST_DB_USERNAME=usbshield_test (database owner), not an application/admin role')
  }
  if (!password) {
    throw new Error('Set USBSHIELD_TEST_DB_PASSWORD for usbshield_test before running browser live tests')
  }
  const schema = `usbshield_e2e_${randomUUID().replaceAll('-', '')}`
  const jdbcUrl = `jdbc:postgresql://${host}:${port}/usbshield_browser_test?currentSchema=${schema}`
  return {
    schema,
    jdbcUrl,
    // Both Spring's native properties and the application's compatibility
    // variables must point to the test DB.  An inherited SPRING_DATASOURCE_URL
    // takes precedence over application.yml's ${USBSHIELD_DB_URL} placeholder.
    env: {
      USBSHIELD_DB_URL: jdbcUrl,
      USBSHIELD_DB_USERNAME: username,
      USBSHIELD_DB_PASSWORD: password,
      SPRING_DATASOURCE_URL: jdbcUrl,
      SPRING_DATASOURCE_USERNAME: username,
      SPRING_DATASOURCE_PASSWORD: password,
      SPRING_DATASOURCE_DRIVER_CLASS_NAME: 'org.postgresql.Driver',
    },
    // Spring Boot command-line properties override inherited environment
    // variables.  Do not include the password in process arguments.
    datasourceArgs: [
      `--spring.datasource.url=${jdbcUrl}`,
      `--spring.datasource.hikari.jdbc-url=${jdbcUrl}`,
      `--spring.datasource.username=${username}`,
      '--spring.datasource.driver-class-name=org.postgresql.Driver',
      '--spring.profiles.active=default',
    ],
    // Flyway creates the disposable schema and applies V1/V2 on first boot;
    // Hibernate VALIDATES it on every boot (including the live-server restart).
    hibernateArgs: [
      `--spring.jpa.properties.hibernate.default_schema=${schema}`,
      '--spring.jpa.hibernate.ddl-auto=validate',
      '--spring.flyway.enabled=true',
      `--spring.flyway.schemas=${schema}`,
      `--spring.flyway.default-schema=${schema}`,
      '--spring.flyway.create-schemas=true',
      '--spring.flyway.baseline-on-migrate=false',
    ],
  }
}

// A local PowerShell session can contain overrides from production setup.
// Pass a clean child environment and fail-closed CLI URL, never trust an
// inherited JDBC/Hikari/JNDI datasource property for browser integration.
export function postgresBrowserChildEnv(parentEnv, browserDb) {
  const childEnv = { ...parentEnv }
  for (const key of Object.keys(childEnv)) {
    // Hikari-specific credentials and externally injected JSON/config can
    // bypass ordinary datasource URL bindings; do not inherit any of them.
    if (/^SPRING_DATASOURCE_/i.test(key) || /^SPRING_APPLICATION_JSON$/i.test(key) ||
        /^SPRING_CONFIG_/i.test(key) || /^SPRING_FLYWAY_/i.test(key) || /^FLYWAY_/i.test(key)) {
      delete childEnv[key]
    }
  }
  return { ...childEnv, ...browserDb.env }
}

/** Drops only the schema owned by this test run, after the backend JVM exits.
 * Uses the PostgreSQL JDBC driver already packaged into the Admin Server JAR.
 */
export async function cleanupPostgresBrowserSchema(browserDb, { java, jar, cwd, parentEnv = process.env, timeoutMs = 20000 }) {
  if (!browserDb || !/^usbshield_e2e_[0-9a-f]{32}$/.test(browserDb.schema) ||
      !browserDb.jdbcUrl?.includes(`/usbshield_browser_test?currentSchema=${browserDb.schema}`) ||
      !java || !jar) {
    throw new Error('Refusing unsafe browser schema cleanup parameters')
  }
  const childEnv = postgresBrowserChildEnv(parentEnv, browserDb)
  await new Promise((resolve, reject) => {
    const child = spawn(java, ['-jar', jar, `--usbshield.cleanup-browser-test-schema=${browserDb.schema}`], {
      cwd, env: childEnv, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'],
    })
    let errorOutput = ''
    child.stderr?.on('data', chunk => { errorOutput = (errorOutput + chunk).slice(-4000) })
    const timer = setTimeout(() => child.kill(), timeoutMs)
    child.on('error', error => { clearTimeout(timer); reject(error) })
    child.on('exit', (code, signal) => {
      clearTimeout(timer)
      if (code === 0) resolve()
      else reject(new Error(`Browser test schema cleanup failed: exit=${code} signal=${signal}; ${errorOutput}`))
    })
  })
}
