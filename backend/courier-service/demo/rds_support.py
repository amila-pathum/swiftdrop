"""RDS demo helpers. Secrets stay in the child process environment."""
import os
from pathlib import Path
import shlex
import subprocess

ROOT = Path(__file__).resolve().parents[3]


def _read(path):
    values = {}
    if path.exists():
        for line in path.read_text().splitlines():
            parts = shlex.split(line, comments=True)
            if parts and '=' in parts[0]:
                name, value = parts[0].split('=', 1)
                values[name] = value
    return values


def rds_settings():
    """RDS settings as un-prefixed names: RDS_* from .env, else a legacy .env.rds."""
    env = _read(ROOT / '.env')
    if env.get('RDS_DB_HOST'):
        return {name[4:]: value for name, value in env.items() if name.startswith('RDS_')}
    return _read(ROOT / '.env.rds')


def sql(service, statement):
    values = rds_settings()
    prefix = service.upper()
    environment = os.environ.copy()
    environment.update(PGHOST=values['DB_HOST'], PGPORT=values.get('DB_PORT', '5432'),
        PGSSLMODE='require', PGCONNECT_TIMEOUT='10', PGUSER=values[prefix+'_DB_USERNAME'],
        PGPASSWORD=values[prefix+'_DB_PASSWORD'], PGDATABASE=values[prefix+'_DB_NAME'])
    # Use the same native-host JDBC route as the services. Docker's network can
    # have different routing/firewall behavior for the remote RDS endpoint.
    output = ROOT / 'backend/courier-service/target/rds-demo'
    output.mkdir(parents=True, exist_ok=True)
    helper = output / 'RdsDemoSql.java'
    helper.write_text('''import java.sql.*;
class RdsDemoSql {
 public static void main(String[] args) throws Exception {
  String host=System.getenv("PGHOST"),port=System.getenv("PGPORT"),db=System.getenv("PGDATABASE");
  String url="jdbc:postgresql://"+host+":"+port+"/"+db+"?sslmode=require&connectTimeout=8&socketTimeout=20";
  try(Connection c=DriverManager.getConnection(url,System.getenv("PGUSER"),System.getenv("PGPASSWORD"));
      Statement s=c.createStatement()) {
   boolean result=s.execute(new String(System.in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
   while(true) {
    if(result)try(ResultSet r=s.getResultSet()) {
     while(r.next()) { for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
      if(i>1)System.out.print("|");String value=r.getString(i);if(value!=null)System.out.print(value);
     } System.out.println(); }
    }
    else if(s.getUpdateCount()==-1)break;
    result=s.getMoreResults();
   }
  }
 }
}''')
    drivers = sorted((Path.home()/'.m2/repository/org/postgresql/postgresql').glob('*/*.jar'))
    if not drivers:
        raise RuntimeError('Build Courier first to install its PostgreSQL JDBC driver')
    result = subprocess.run(['java','--class-path',str(drivers[-1]),str(helper)],
        input=statement.encode(), capture_output=True, env=environment)
    if result.returncode:
        raise RuntimeError(result.stderr.decode())
    return result.stdout.decode().strip()


def quote(value):
    return 'NULL' if value is None else "'" + str(value).replace("'", "''") + "'"
