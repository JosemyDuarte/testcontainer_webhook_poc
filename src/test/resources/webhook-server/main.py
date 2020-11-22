#!/usr/bin/python3

import os
from flask import Flask
from flask import jsonify
from flask import request

app = Flask(__name__)

last_request = None


@app.route('/registerRequest', methods=['POST'])
def webhook():
    global last_request
    last_request = request.get_json()
    print("Received")
    return jsonify(last_request)


@app.route('/lastRequest', methods=['GET'])
def return_last_request():
    return ('', 204) if last_request is None else jsonify(last_request)


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=os.getenv("PORT"))
