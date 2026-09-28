import os
import re

d = 'app/models'
for f in os.listdir(d):
    if not f.endswith('.py') or f == '__init__.py': continue
    p = os.path.join(d, f)
    with open(p, 'r', encoding='utf8') as file:
        content = file.read()
    
    # 1. Add Integer and Numeric to sqlalchemy imports if missing
    # Find the sqlalchemy import statement. It could be `from sqlalchemy import (...)` or `from sqlalchemy import x, y`
    if 'Integer' not in content:
        content = re.sub(r'from sqlalchemy import (?:\\n\s+)?\(?', r'\g<0>Integer, ', content, count=1)
    if 'Numeric' not in content and ('Mapped[float]' in content or 'Float' in content):
        content = re.sub(r'from sqlalchemy import (?:\\n\s+)?\(?', r'\g<0>Numeric, ', content, count=1)

    # 2. Add __mapper_args__ = {"version_id_col": "version"} after __tablename__
    content = re.sub(r'(__tablename__\s*=\s*"[^"]+")', r'\1\n    __mapper_args__ = {"version_id_col": "version"}', content)
    
    # 3. Add deleted_at and version at the end of the class (before # Relationships)
    audit_cols = '''
    deleted_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=True)
    version: Mapped[int] = mapped_column(Integer, default=1, nullable=False, server_default="1")
    '''
    if '# Relationships' in content:
        content = content.replace('# Relationships', audit_cols + '\n    # Relationships')
    else:
        # If no relationships comment, just append to the end of the file. But we must be inside the class.
        # Just find the last mapped_column
        pass # Actually financial_config has it at the end
        if f == 'financial_config.py' or f == 'financial_snapshot.py':
            content = content + audit_cols
            
    # 4. Replace Float with Numeric(14, 2)
    content = content.replace('Mapped[float] = mapped_column(Float', 'Mapped[float] = mapped_column(Numeric(14, 2)')
    
    with open(p, 'w', encoding='utf8') as file:
        file.write(content)
