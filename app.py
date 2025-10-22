from flask import Flask, render_template, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from datetime import datetime
import os


app = Flask(__name__)


# Configure database from DATABASE_URL env var or fallback
DATABASE_URL = os.environ.get('DATABASE_URL', 'postgresql://postgres:password@localhost:5432/tododb')
app.config['SQLALCHEMY_DATABASE_URI'] = DATABASE_URL
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False


db = SQLAlchemy(app)


class Task(db.Model):
__tablename__ = 'tasks'
id = db.Column(db.Integer, primary_key=True)
description = db.Column(db.Text, nullable=False)
created_at = db.Column(db.DateTime, default=datetime.utcnow)


def to_dict(self):
return {
'id': self.id,
'description': self.description,
'created_at': self.created_at.isoformat()
}


@app.route('/')
def index():
return render_template('index.html')


# API to get tasks
@app.route('/api/tasks', methods=['GET'])
def get_tasks():
tasks = Task.query.order_by(Task.created_at.desc()).all()
return jsonify([t.to_dict() for t in tasks])


# API to add a task
@app.route('/api/tasks', methods=['POST'])
def add_task():
data = request.get_json() or {}
desc = data.get('description', '').strip()
if not desc:
return jsonify({'error': 'Description required'}), 400
t = Task(description=desc)
db.session.add(t)
db.session.commit()
return jsonify(t.to_dict()), 201


# API to delete a task
@app.route('/api/tasks/<int:task_id>', methods=['DELETE'])
def delete_task(task_id):
t = Task.query.get(task_id)
if not t:
return jsonify({'error': 'Task not found'}), 404
db.session.delete(t)
db.session.commit()
return jsonify({'result': 'deleted'})


if __name__ == '__main__':
# If running directly, ensure DB tables exist (for convenience)
with app.app_context():
db.create_all()
app.run(debug=True)
